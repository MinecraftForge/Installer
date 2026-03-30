/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;

public class SwingUtil {
    public static JButton createLogButton() {
        JButton button = new JButton("Open log");
        button.addActionListener(ev -> {
            File file = new File("installer.log");
            try {
                if (file.exists())
                    Desktop.getDesktop().open(file);
            } catch (IOException e) {
                // Handle any errors that may occur during file opening
                e.printStackTrace();

                // Show the error. Does not happen on headless.
                // Can only occur on NON-Headless, so its safe to just show the error
                JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        return button;
    }

    /**
     * Applies a new global font to all Swing UI components instantiated after this method call
     * @param fontFamily The name of the desired font to apply
     */
    static void applyGlobalFont(String fontFamily) {
        UIDefaults defaults = UIManager.getDefaults();
        Enumeration<Object> keys = defaults.keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = defaults.get(key);
            if (value instanceof FontUIResource) {
                FontUIResource font = (FontUIResource) value;
                defaults.put(key, new FontUIResource(fontFamily, font.getStyle(), font.getSize()));
            }
        }
    }
}
