package net.minecraftforge.installer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class SwingUtil {
    public static JButton createlogButton() {
        JButton button = new JButton("Open Log");
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
}
