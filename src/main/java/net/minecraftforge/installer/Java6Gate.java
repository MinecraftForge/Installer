/*
 * Installer
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.minecraftforge.installer;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Method;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import com.google.common.base.Strings;

public class Java6Gate
{

    public static void main(String[] args) throws Exception
    {

        try
        {
            String versionProperty = System.getProperty("java.specification.version");
            int version;
            if (versionProperty.startsWith("1."))
            {
                version = Integer.parseInt(versionProperty.substring(2));
            }
            else
            {
                version = Integer.parseInt(versionProperty); // java 9 and onwards
            }
            if (version < 8)
            {
                displayErrorMessage();
                System.exit(-1);
            }
        }
        catch (NumberFormatException e)
        {
            System.err.println("Java version not a number? Ignoring.");
        }

        // cannot reference directly, because this class is compiled separately for java 6
        Class<?> mainClass = Class.forName("net.minecraftforge.installer.SimpleInstaller");
        Method main = mainClass.getMethod("main", String[].class);
        main.invoke(null, (Object) args);
    }

    private static void displayErrorMessage()
    {
        System.err.println("");
        System.err.println(Strings.repeat("=", 80));
        System.err.println("Forge requires Java 8 to be installed.");
        System.err.println("Please install the latest Java 8 appropriate for your System from https://java.com/download/.");
        System.err.println(Strings.repeat("=", 80));
        System.err.println("");

        if (!GraphicsEnvironment.isHeadless()) {
            final Object mutex = new Object();

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    } catch (Exception ignored) {
                    }

                    JLabel label = new JLabel();
                    Font font = label.getFont();

                    // create some css from the label's font
                    StringBuilder style = new StringBuilder("font-family:" + font.getFamily() + ";")
                            .append("font-weight:")
                            .append(font.isBold() ? "bold" : "normal")
                            .append(";")
                            .append("font-size:")
                            .append(font.getSize()).append("pt;");

                    JTextPane text = new JTextPane();
                    text.setContentType("text/html");
                    text.setText("<html><body style=\"" + style + "\">" +
                            "<strong>Forge requires Java 8 to be installed.</strong><br />" +
                            "Please install the latest Java 8 appropriate for your System from <a href=\"https://java.com/download/\">java.com/download</a>." +
                            "<br /><br />Thank you. The program will exit now." +
                            "</body></html>");

                    text.setEditable(false);
                    text.setHighlighter(null);
                    text.setBackground(label.getBackground());

                    text.setMargin(new Insets(20, 20, 20, 20));

                    text.addHyperlinkListener(new HyperlinkListener() {
                        @Override
                        public void hyperlinkUpdate(HyperlinkEvent e) {
                            if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED))
                                try {
                                    Desktop.getDesktop().browse(e.getURL().toURI());
                                } catch (Exception ignored) {
                                }
                        }
                    });

                    final JFrame frame = new JFrame("Java 8 required");

                    JButton button = new JButton("Exit");
                    button.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            frame.dispose();
                        }
                    });

                    JPanel panel = new JPanel();
                    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                    panel.add(text);
                    panel.add(button);
                    panel.add(Box.createVerticalStrut(20));

                    frame.setContentPane(panel);
                    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                    frame.setResizable(false);
                    frame.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosed(WindowEvent e) {
                            synchronized (mutex) {
                                mutex.notify();
                            }
                        }
                    });

                    frame.pack();
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                    frame.toFront();
                }
            });

            synchronized (mutex) {
                try {
                    mutex.wait(); // wait for the window to be closed
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }
    }

}
