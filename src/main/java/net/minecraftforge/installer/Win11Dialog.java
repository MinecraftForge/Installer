/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.Line2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

final class Win11Dialog {
    private static final Dimension CAPTION_BUTTON_SIZE = new Dimension(46, 30);

    // Note: These are here because Java 8 doesn't support declaring static fields inside anonymous classes
    private static final Color WINDOW_HIT_TEST_COLOR = new Color(255, 255, 255, 1);
    private static final Color TITLE_BAR_OVERLAY = new Color(220, 220, 220, 0);
    private static final Color CLOSE_BUTTON_HOVER = new Color(196, 43, 28);
    private static final Color CLOSE_BUTTON_PRESSED = new Color(143, 32, 20);
    private static final Color CLOSE_BUTTON_GLYPH = new Color(32, 32, 32);

    private Win11Dialog() {}

    /**
     * Swing does not support creating decorated windows that are non-opaque, the latter of which is required for the
     * Mica backdrop to work. So let's make our own window decoration that looks close to the native title bar.
     * @param optionPane The option pane to display inside the dialog
     * @param title The window title to display on the dialog
     * @return A new JDialog instance with custom window decorations and support for the Mica backdrop on Windows 11.
     * @see Win11MicaEffect
     */
    static JDialog create(JOptionPane optionPane, JPanel installerPanel, String title) {
        JDialog installerDialog = new JDialog(null, title, Dialog.ModalityType.APPLICATION_MODAL);
        installerDialog.setUndecorated(true);
        installerDialog.setType(Window.Type.NORMAL);
        installerDialog.setContentPane(createInstallerChrome(installerDialog, optionPane));
        installerDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                optionPane.setValue(JOptionPane.CLOSED_OPTION);
            }
        });
        optionPane.addPropertyChangeListener(event -> {
            String propertyName = event.getPropertyName();
            if (!installerDialog.isVisible() || event.getSource() != optionPane) {
                return;
            }
            if (!JOptionPane.VALUE_PROPERTY.equals(propertyName) && !JOptionPane.INPUT_VALUE_PROPERTY.equals(propertyName)) {
                return;
            }
            installerDialog.setVisible(false);
        });
        installerDialog.pack();
        installerDialog.setLocationRelativeTo(null);
        optionPane.selectInitialValue();
        Win11MicaEffect.prepare(optionPane);
        Win11MicaEffect.prepare(installerPanel);
        Win11MicaEffect.install(installerDialog);
        return installerDialog;
    }

    private static JPanel createInstallerChrome(JDialog installerDialog, JOptionPane optionPane) {
        JPanel chrome = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                try {
                    g2.setColor(WINDOW_HIT_TEST_COLOR);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                } finally {
                    g2.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        chrome.setOpaque(false);
        chrome.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        chrome.add(createTitleBar(installerDialog, optionPane), BorderLayout.NORTH);
        chrome.add(optionPane, BorderLayout.CENTER);
        return chrome;
    }

    private static JPanel createTitleBar(JDialog installerDialog, JOptionPane optionPane) {
        JPanel titleBar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(TITLE_BAR_OVERLAY);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                } finally {
                    g2.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        titleBar.setOpaque(false);
        titleBar.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

        JLabel titleLabel = new JLabel(installerDialog.getTitle());
        titleLabel.setHorizontalAlignment(SwingConstants.LEFT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        titleBar.add(titleLabel, BorderLayout.WEST);

        JButton closeButton = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

                    ButtonModel model = getModel();
                    if (model.isPressed()) {
                        g2.setColor(CLOSE_BUTTON_PRESSED);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                    } else if (model.isRollover()) {
                        g2.setColor(CLOSE_BUTTON_HOVER);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                    }

                    g2.setColor(model.isPressed() || model.isRollover() ? Color.WHITE : CLOSE_BUTTON_GLYPH);
                    g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2;
                    g2.draw(new Line2D.Float(cx - 4.5f, cy - 4.5f, cx + 4.5f, cy + 4.5f));
                    g2.draw(new Line2D.Float(cx + 4.5f, cy - 4.5f, cx - 4.5f, cy + 4.5f));
                } finally {
                    g2.dispose();
                }
            }
        };
        closeButton.setFocusable(false);
        closeButton.setOpaque(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setBorderPainted(false);
        closeButton.setRolloverEnabled(true);
        closeButton.setMargin(new Insets(8, 12, 8, 12));
        closeButton.setPreferredSize(CAPTION_BUTTON_SIZE);
        closeButton.setMinimumSize(CAPTION_BUTTON_SIZE);
        closeButton.setMaximumSize(CAPTION_BUTTON_SIZE);
        closeButton.setToolTipText("Close");
        closeButton.addActionListener(ignored -> optionPane.setValue(JOptionPane.CLOSED_OPTION));
        titleBar.add(closeButton, BorderLayout.EAST);

        Point[] dragOffset = {null};
        MouseAdapter dragListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOffset[0] = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset[0] == null) {
                    return;
                }
                Point screen = e.getLocationOnScreen();
                installerDialog.setLocation(screen.x - dragOffset[0].x, screen.y - dragOffset[0].y);
            }
        };
        titleBar.addMouseListener(dragListener);
        titleBar.addMouseMotionListener(dragListener);
        titleLabel.addMouseListener(dragListener);
        titleLabel.addMouseMotionListener(dragListener);

        return titleBar;
    }
}

