/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.plaf.UIResource;

/// The colours used by the Windows 11 installer window, matching the user's light/dark mode preference.
/// @see Win11MicaEffect#useDarkMode()
sealed interface Win11Theme {
    Win11Theme CURRENT = Win11MicaEffect.useDarkMode() ? Dark.INSTANCE : Light.INSTANCE;

    Color transparent();
    Color windowBackground();
    Color windowHitTest();
    Color titleBarOverlay();
    Color closeButtonHover();
    Color closeButtonPressed();
    Color closeButtonGlyph();
    Color text();
    Color control();
    Color controlBorder();
    Color buttonHover();
    Color buttonPressed();
    Color focusRing();

    /// Swing's Windows LAF is based on the legacy Windows XP theme system which looks dated and wrong on Win11,
    /// especially with dark mica enabled. This method sets up a custom theme that better matches Win11's LAF.
    default void applyTo(Component component) {
        if (component instanceof JLabel || component instanceof AbstractButton || component instanceof JTextField) {
            if (component.getForeground() instanceof UIResource)
                component.setForeground(text());
        }

        // Buttons that don't paint their own border or content area (e.g. the sponsor link and the custom title bar
        // close button) are already custom styled, so leave those alone
        if (component instanceof JButton button && button.isBorderPainted() && button.isContentAreaFilled()) {
            Win11MicaEffect.Win11ButtonUI.install(button);
        } else if (component instanceof JTextField textField) {
            if (textField.getBackground() instanceof UIResource)
                textField.setBackground(control());
            if (textField.getCaretColor() instanceof UIResource)
                textField.setCaretColor(text());
            if (textField.getBorder() instanceof UIResource) {
                // Replace the native border while keeping the same insets to avoid layout changes
                Insets insets = textField.getInsets();
                textField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(controlBorder()),
                    BorderFactory.createEmptyBorder(insets.top - 1, insets.left - 1, insets.bottom - 1, insets.right - 1)
                ));
            }
        }
    }

    record Light(
        Color transparent, Color windowBackground, Color windowHitTest, Color titleBarOverlay,
        Color closeButtonHover, Color closeButtonPressed, Color closeButtonGlyph,
        Color text, Color control, Color controlBorder, Color buttonHover, Color buttonPressed,
        Color focusRing
    ) implements Win11Theme {
        static final Light INSTANCE = new Light(
            new Color(220, 220, 220, 0), // transparent
            new Color(220, 220, 220, 1), // windowBackground
            new Color(255, 255, 255, 1), // windowHitTest
            new Color(220, 220, 220, 0), // titleBarOverlay
            new Color(196, 43, 28),      // closeButtonHover
            new Color(143, 32, 20),      // closeButtonPressed
            new Color(32, 32, 32),       // closeButtonGlyph
            new Color(0, 0, 0),          // text
            new Color(251, 251, 251),    // control
            new Color(212, 212, 212),    // controlBorder
            new Color(246, 246, 246),    // buttonHover
            new Color(240, 240, 240),    // buttonPressed
            new Color(0, 95, 184)        // focusRing
        );
    }

    record Dark(
        Color transparent, Color windowBackground, Color windowHitTest, Color titleBarOverlay,
        Color closeButtonHover, Color closeButtonPressed, Color closeButtonGlyph,
        Color text, Color control, Color controlBorder, Color buttonHover, Color buttonPressed,
        Color focusRing
    ) implements Win11Theme {
        static final Dark INSTANCE = new Dark(
            new Color(32, 32, 32, 0),    // transparent
            new Color(32, 32, 32, 1),    // windowBackground
            new Color(0, 0, 0, 1),       // windowHitTest
            new Color(32, 32, 32, 0),    // titleBarOverlay
            new Color(196, 43, 28),      // closeButtonHover
            new Color(150, 35, 22),      // closeButtonPressed
            new Color(255, 255, 255),    // closeButtonGlyph
            new Color(255, 255, 255),    // text
            new Color(45, 45, 45),       // control
            new Color(65, 65, 65),       // controlBorder
            new Color(55, 55, 55),       // buttonHover
            new Color(39, 39, 39),       // buttonPressed
            new Color(0, 95, 184)        // focusRing
        );
    }
}
