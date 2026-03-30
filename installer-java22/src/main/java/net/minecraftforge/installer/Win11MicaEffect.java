/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

final class Win11MicaEffect {
    private static final Color TRANSPARENT = new Color(220, 220, 220, 0);
    private static final Color WINDOW_BACKGROUND = new Color(220, 220, 220, 1);

    private Win11MicaEffect() {}

    /// Swing paints an opaque background by default for all components, which hides the Mica backdrop effect.
    /// This method recursively undoes that so that DWM is responsible for painting the window background.
    static void prepare(Component component) {
        if (isUnsupportedPlatform())
            return;

        if (component instanceof JPanel || component instanceof JOptionPane || component instanceof JRadioButton) {
            JComponent jComponent = (JComponent) component;
            jComponent.setOpaque(false);
            jComponent.setBackground(TRANSPARENT);
        }

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                prepare(child);
            }
        }
    }

    static void install(JDialog dialog) {
        if (isUnsupportedPlatform())
            return;

        dialog.getRootPane().setOpaque(false);
        dialog.getLayeredPane().setOpaque(false);
        if (dialog.getContentPane() instanceof JComponent contentPane) {
            contentPane.setOpaque(false);
            contentPane.setBackground(TRANSPARENT);
        }
        prepare(dialog.getRootPane());

        dialog.setBackground(WINDOW_BACKGROUND);

        // In order for the Mica effect to apply correctly on a Swing window, we need to ensure a repaint after applying
        // for timing reasons. If the window is already showing, we can repaint immediately, otherwise wait for the
        // window to be opened first.
        Runnable apply = () -> {
            try {
                applyTo(dialog);
                dialog.invalidate();
                dialog.validate();
                dialog.repaint();
                dialog.getRootPane().repaint();
            } catch (Throwable _) {}
        };

        if (dialog.isShowing()) {
            apply.run();
            return;
        }

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                dialog.removeWindowListener(this);
                apply.run();
            }
        });
    }

    private static void applyTo(Dialog dialog) throws Throwable {
        if (!dialog.isDisplayable())
            return;

        var linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            // Lookup the necessary Windows APIs
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32", arena);
            SymbolLookup dwmapi = SymbolLookup.libraryLookup("dwmapi", arena);

            MemorySegment hwnd = findDialogWindow(dialog, arena, user32);
            if (MemorySegment.NULL.equals(hwnd))
                return;

            MethodHandle setWindowPos = linker.downcallHandle(
                user32.find("SetWindowPos").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT
                )
            );
            MethodHandle dwmSetWindowAttribute = linker.downcallHandle(
                dwmapi.find("DwmSetWindowAttribute").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT
                )
            );
            MethodHandle dwmExtendFrameIntoClientArea = linker.downcallHandle(
                dwmapi.find("DwmExtendFrameIntoClientArea").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            promoteToTaskbarWindow(hwnd, user32, setWindowPos);

            // Win11 defaults to a rounded corner preference for decorated windows, but for backwards-compatibility it
            // defaults to sharp corners for undecorated windows. This tells Win11 that we want rounded corners for our
            // undecorated dialog window
            MemorySegment cornerPreference = arena.allocate(ValueLayout.JAVA_INT);
            cornerPreference.set(ValueLayout.JAVA_INT, 0, 2); // DWMWCP_ROUND
            int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
            int _ = (int) dwmSetWindowAttribute.invokeExact(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, cornerPreference, Integer.BYTES);

            // Tell DWM that we'd like to opt-into the main window backdrop type rather than the backwards-compatible
            // default of no backdrop effect. This is the main bit that actually enables the Mica backdrop effect - the
            // rest of the code is mostly workarounds for Swing limitations
            MemorySegment backdropType = arena.allocate(ValueLayout.JAVA_INT);
            backdropType.set(ValueLayout.JAVA_INT, 0, 2); // DWMSBT_MAINWINDOW
            int DWMWA_SYSTEMBACKDROP_TYPE = 38;
            int hresult = (int) dwmSetWindowAttribute.invokeExact(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, backdropType, Integer.BYTES);
            if (hresult != 0)
                return;

            // Tell DWM to paint the entire window background with the Mica brush rather than the default of only the
            // title bar.
            MemorySegment margins = arena.allocate(4L * Integer.BYTES, Integer.BYTES);
            margins.set(ValueLayout.JAVA_INT, 0L, -1);
            margins.set(ValueLayout.JAVA_INT, Integer.BYTES, -1);
            margins.set(ValueLayout.JAVA_INT, 2L * Integer.BYTES, -1);
            margins.set(ValueLayout.JAVA_INT, 3L * Integer.BYTES, -1);
            int _ = (int) dwmExtendFrameIntoClientArea.invokeExact(hwnd, margins);

            // Comply with the remarks in Windows' API docs to ensure that the style changes apply
            refreshWindowFrame(hwnd, setWindowPos);
        }
    }

    /// Attempt to find the window handle (HWND) of the dialog window by querying for a window with its title first,
    /// falling back to the foreground window if no window with this dialog's window title is found.
    private static MemorySegment findDialogWindow(Dialog dialog, Arena arena, SymbolLookup user32) throws Throwable {
        var linker = Linker.nativeLinker();
        MethodHandle findWindowW = linker.downcallHandle(
                user32.find("FindWindowW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        String title = dialog.getTitle();
        if (title != null && !title.isBlank()) {
            MemorySegment windowTitle = arena.allocateFrom(ValueLayout.JAVA_CHAR, (title + '\0').toCharArray());
            MemorySegment hwnd = (MemorySegment) findWindowW.invokeExact(MemorySegment.NULL, windowTitle);
            if (!MemorySegment.NULL.equals(hwnd))
                return hwnd;
        }

        MethodHandle getForegroundWindow = linker.downcallHandle(
                user32.find("GetForegroundWindow").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS)
        );

        return (MemorySegment) getForegroundWindow.invokeExact();
    }

    /// Ensures that the dialog box is visible in the taskbar by removing the tool window style and adding the app
    /// window style. This is a workaround for Swing hiding the dialog box from the taskbar when undecorated and
    /// non-opaque due to it expecting that the window is invisible, however in practice it is visible as DWM will draw
    /// the window background for us which is what we want for the Mica effect to be visible.
    /// @see [#prepare(Component) 
    private static void promoteToTaskbarWindow(MemorySegment hwnd, SymbolLookup user32, MethodHandle setWindowPos) throws Throwable {
        var linker = Linker.nativeLinker();
        MethodHandle getWindowLongW = linker.downcallHandle(
                user32.find("GetWindowLongW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
        );
        MethodHandle setWindowLongW = linker.downcallHandle(
                user32.find("SetWindowLongW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        );

        int WS_EX_TOOLWINDOW = 0x00000080;
        int WS_EX_APPWINDOW = 0x00040000;
        int GWL_EXSTYLE = -20;
        int exStyle = (int) getWindowLongW.invokeExact(hwnd, GWL_EXSTYLE);
        int newExStyle = (exStyle | WS_EX_APPWINDOW) & ~WS_EX_TOOLWINDOW;
        if (newExStyle == exStyle)
            return;

        int _ = (int) setWindowLongW.invokeExact(hwnd, GWL_EXSTYLE, newExStyle);
        refreshWindowFrame(hwnd, setWindowPos);
    }

    /// The [docs](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowpos#remarks) mention
    /// that we should call SetWindowPos with the SWP_FRAMECHANGED flag after calling SetWindowLong to ensure that
    /// style updates apply on Windows Vista onwards. This is done after the DWM calls just in case those count too.
    /// @see [#promoteToTaskbarWindow(MemorySegment, SymbolLookup, MethodHandle) 
    private static void refreshWindowFrame(MemorySegment hwnd, MethodHandle setWindowPos) throws Throwable {
        // SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED;
        int flags = 0x0001 | 0x0002 | 0x0004 | 0x0010 | 0x0020;
        int _ = (int) setWindowPos.invokeExact(hwnd, MemorySegment.NULL, 0, 0, 0, 0, flags);
    }

    /// @return true if the current platform is Windows 11
    static boolean isSupported() {
        return !isUnsupportedPlatform();
    }

    private static boolean isUnsupportedPlatform() {
        final class LazyInit {
            private LazyInit() {}
            private static final boolean IS_UNSUPPORTED
                    = !System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("windows 11");
        }
        return LazyInit.IS_UNSUPPORTED;
    }
}
