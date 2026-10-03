/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
import java.util.concurrent.Callable;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.plaf.basic.BasicButtonUI;

final class Win11MicaEffect {
    private Win11MicaEffect() {}

    /// Swing paints an opaque background by default for all components, which hides the Mica backdrop effect.
    /// This method recursively undoes that so that DWM is responsible for painting the window background.
    static void prepare(Component component) {
        if (isUnsupportedPlatform())
            return;

        if (component instanceof JPanel || component instanceof JOptionPane || component instanceof JRadioButton) {
            JComponent jComponent = (JComponent) component;
            jComponent.setOpaque(false);
            jComponent.setBackground(Win11Theme.CURRENT.transparent());
        }

        Win11Theme.CURRENT.applyTo(component);

        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                prepare(child);
            }
        }
    }

    static final class Win11ButtonUI extends BasicButtonUI {
        private static final int CURVE_AMOUNT = 8;

        static void install(JButton button) {
            if (button.getUI() instanceof Win11ButtonUI)
                return;

            // Keep the same insets as the native button to avoid layout changes
            Insets insets = button.getInsets();
            button.setUI(new Win11ButtonUI());
            button.setBorder(BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right));
            button.setOpaque(false);
            button.setRolloverEnabled(true);
        }

        @Override
        public void paint(Graphics graphics, JComponent component) {
            AbstractButton button = (AbstractButton) component;
            ButtonModel model = button.getModel();
            Win11Theme theme = Win11Theme.CURRENT;
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (model.isPressed() && model.isArmed())
                    g2.setColor(theme.buttonPressed());
                else if (model.isRollover())
                    g2.setColor(theme.buttonHover());
                else
                    g2.setColor(theme.control());
                g2.fillRoundRect(0, 0, button.getWidth() - 1, button.getHeight() - 1, CURVE_AMOUNT, CURVE_AMOUNT);

                g2.setColor(theme.controlBorder());
                g2.drawRoundRect(0, 0, button.getWidth() - 1, button.getHeight() - 1, CURVE_AMOUNT, CURVE_AMOUNT);
            } finally {
                g2.dispose();
            }
            super.paint(graphics, component);
        }

        @Override
        protected void paintButtonPressed(Graphics graphics, AbstractButton button) {
            // Already handled in paint()
        }

        @Override
        protected void paintFocus(Graphics graphics, AbstractButton button, Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Win11Theme.CURRENT.focusRing());
                g2.drawRoundRect(1, 1, button.getWidth() - 3, button.getHeight() - 3, CURVE_AMOUNT - 2, CURVE_AMOUNT - 2);
            } finally {
                g2.dispose();
            }
        }
    }

    static void install(JDialog dialog) throws Exception {
        if (isUnsupportedPlatform())
            return;

        dialog.getRootPane().setOpaque(false);
        dialog.getLayeredPane().setOpaque(false);
        if (dialog.getContentPane() instanceof JComponent contentPane) {
            contentPane.setOpaque(false);
            contentPane.setBackground(Win11Theme.CURRENT.transparent());
        }
        prepare(dialog.getRootPane());

        dialog.setBackground(Win11Theme.CURRENT.windowBackground());

        // In order for the Mica effect to apply correctly on a Swing window, we need to ensure a repaint after applying
        // for timing reasons. If the window is already showing, we can repaint immediately, otherwise wait for the
        // window to be opened first.
        Callable<Void> apply = () -> {
            try {
                applyTo(dialog);
            } catch (Throwable t) {
                if (t instanceof Exception e) throw e;
                else throw new RuntimeException(t);
            }
            dialog.invalidate();
            dialog.validate();
            dialog.repaint();
            dialog.getRootPane().repaint();
            return null;
        };

        if (dialog.isShowing()) {
            apply.call();
            return;
        }

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                dialog.removeWindowListener(this);
                try {
                    apply.call();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
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

            // DWM defaults to light mode for backwards-compatibility, so opt-into dark mode if the user prefers it
            if (useDarkMode()) {
                MemorySegment useDarkMode = arena.allocate(ValueLayout.JAVA_BOOLEAN);
                useDarkMode.set(ValueLayout.JAVA_BOOLEAN, 0, true);
                int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
                int _ = (int) dwmSetWindowAttribute.invokeExact(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, useDarkMode, Integer.BYTES);
            }

            // Tell DWM that we'd like to opt-into the main window backdrop type rather than the backwards-compatible
            // default of no backdrop effect. This is the main bit that actually enables the Mica backdrop effect - the
            // rest of the code is mostly workarounds for Swing limitations
            MemorySegment backdropType = arena.allocate(ValueLayout.JAVA_INT);
            // 2 = Mica (DWMSBT_MAINWINDOW), 3 = Acrylic (DWMSBT_TRANSIENTWINDOW), 4 = Mica Alt (DWMSBT_TABBEDWINDOW)
            int backdropTypePreference = Integer.getInteger("forgeinstaller.backdroptype", 2);
            backdropType.set(ValueLayout.JAVA_INT, 0, backdropTypePreference);
            int DWMWA_SYSTEMBACKDROP_TYPE = 38;
            int hresult = (int) dwmSetWindowAttribute.invokeExact(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, backdropType, Integer.BYTES);
            if (hresult != 0)
                throw new RuntimeException("DwmSetWindowAttribute failed with HRESULT 0x" + Integer.toHexString(hresult));

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

    /// @return true if Mica is supported and the user prefers dark mode for apps in Windows' personalisation settings.
    /// Can be overridden with the `forgeinstaller.darkmode` system property.
    static boolean useDarkMode() {
        final class LazyInit {
            private LazyInit() {}
            private static final boolean USE_DARK_MODE;
            static {
                boolean isDarkMode = false;
                if (!isUnsupportedPlatform()) {
                    String override = System.getProperty("forgeinstaller.darkmode");
                    if (override != null) {
                        isDarkMode = Boolean.parseBoolean(override);
                    } else {
                        try {
                            isDarkMode = readAppsUseLightTheme() == 0;
                        } catch (Throwable ignored) {
                            // Fallback to light mode if we can't read the preference
                        }
                    }
                }
                USE_DARK_MODE = isDarkMode;
            }
        }
        return LazyInit.USE_DARK_MODE;
    }

    /// Reads the `AppsUseLightTheme` DWORD from the registry, which is 0 when the user has chosen dark mode for apps.
    /// @return the registry value, or -1 if it couldn't be read (e.g. the user has never changed their theme)
    private static int readAppsUseLightTheme() throws Throwable {
        var linker = Linker.nativeLinker();
        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup advapi32 = SymbolLookup.libraryLookup("advapi32", arena);
            MethodHandle regGetValueW = linker.downcallHandle(
                advapi32.find("RegGetValueW").orElseThrow(),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
                )
            );

            MemorySegment HKEY_CURRENT_USER = MemorySegment.ofAddress(0x80000001L);
            MemorySegment subKey = arena.allocateFrom(ValueLayout.JAVA_CHAR,
                "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\0".toCharArray());
            MemorySegment valueName = arena.allocateFrom(ValueLayout.JAVA_CHAR, "AppsUseLightTheme\0".toCharArray());
            MemorySegment data = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment dataSize = arena.allocateFrom(ValueLayout.JAVA_INT, Integer.BYTES);

            int RRF_RT_REG_DWORD = 0x00000010;
            int status = (int) regGetValueW.invokeExact(
                HKEY_CURRENT_USER, subKey, valueName, RRF_RT_REG_DWORD, MemorySegment.NULL, data, dataSize
            );
            if (status != 0) // ERROR_SUCCESS
                return -1;

            return data.get(ValueLayout.JAVA_INT, 0);
        }
    }

    /// @return true if the current platform is Windows 11
    static boolean isSupported() {
        return !isUnsupportedPlatform();
    }

    private static boolean isUnsupportedPlatform() {
        final class LazyInit {
            private LazyInit() {}
            private static final boolean IS_UNSUPPORTED;
            static {
                var useMica = true;
                boolean forceDisableMica = false;
                var isWin11 = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("windows 11");
                if (isWin11) {
                    // Disable Mica if explicitly requested
                    forceDisableMica = !Boolean.parseBoolean(System.getProperty("forgeinstaller.usewin11mica", "true"));
                    if (forceDisableMica)
                        useMica = false;
                } else {
                    // Mica is only supported on Windows 11
                    useMica = false;
                }

//                System.out.println("OS: " + System.getProperty("os.name", "") + ", isWin11: " + isWin11 + ", forceDisableMica: " + forceDisableMica);
                IS_UNSUPPORTED = !useMica;
            }
        }
        return LazyInit.IS_UNSUPPORTED;
    }
}
