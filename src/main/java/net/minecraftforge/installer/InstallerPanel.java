/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;

import net.minecraftforge.installer.actions.Action;
import net.minecraftforge.installer.actions.ActionCanceledException;
import net.minecraftforge.installer.actions.Actions;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.InstallV1;

@SuppressWarnings("unused")
public class InstallerPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private File targetDir;
    private ButtonGroup choiceButtonGroup;
    private JTextField selectedDirText;
    private JLabel infoLabel;
    private JButton sponsorButton;
    private JDialog dialog;
    private JPanel sponsorPanel;
    private JPanel fileEntryPanel;
    private Map<String, Function<ProgressCallback, Action>> actions = new HashMap<>();

    private final InstallV1 profile;
    private final File installer;
    private final String badCerts;

    private class FileSelectAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser dirChooser = new JFileChooser();
            dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            dirChooser.setFileHidingEnabled(false);
            dirChooser.ensureFileIsVisible(targetDir);
            dirChooser.setSelectedFile(targetDir);
            int response = dirChooser.showOpenDialog(InstallerPanel.this);
            switch (response) {
                case JFileChooser.APPROVE_OPTION:
                    targetDir = dirChooser.getSelectedFile();
                    updateFilePath();
                    break;
                default:
                    break;
            }
        }
    }

    private class SelectButtonAction extends AbstractAction {
        private static final long serialVersionUID = 1L;
        @Override
        public void actionPerformed(ActionEvent e) {
            updateFilePath();
        }
    }

    private BufferedImage getImage(String path) {
        try {
            InputStream in = SimpleInstaller.class.getResourceAsStream(path);
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return sneak(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable, R> R sneak(Throwable e) throws E {
        throw (E)e;
    }

    public InstallerPanel(File targetDir, InstallV1 profile, File installer, String badCerts) {
        this.profile = profile;
        this.installer = installer;
        this.badCerts = badCerts;

        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        BufferedImage image = getImage(profile.getLogo());

        JPanel logoSplash = new JPanel();
        logoSplash.setLayout(new BoxLayout(logoSplash, BoxLayout.Y_AXIS));
        ImageIcon icon = new ImageIcon(image);
        JLabel logoLabel = new JLabel(icon);
        logoLabel.setAlignmentX(CENTER_ALIGNMENT);
        logoLabel.setAlignmentY(CENTER_ALIGNMENT);
        logoLabel.setSize(image.getWidth(), image.getHeight());
        logoSplash.add(logoLabel);
        JLabel tag = new JLabel(profile.getWelcome());
        tag.setAlignmentX(CENTER_ALIGNMENT);
        tag.setAlignmentY(CENTER_ALIGNMENT);
        logoSplash.add(tag);
        tag = new JLabel(profile.getVersion());
        tag.setAlignmentX(CENTER_ALIGNMENT);
        tag.setAlignmentY(CENTER_ALIGNMENT);
        logoSplash.add(tag);

        logoSplash.setAlignmentX(CENTER_ALIGNMENT);
        logoSplash.setAlignmentY(TOP_ALIGNMENT);
        this.add(logoSplash);

        sponsorPanel = new JPanel();
        sponsorPanel.setLayout(new BoxLayout(sponsorPanel, BoxLayout.X_AXIS));
        sponsorPanel.setAlignmentX(CENTER_ALIGNMENT);
        sponsorPanel.setAlignmentY(CENTER_ALIGNMENT);

        sponsorButton = new JButton();
        sponsorButton.setAlignmentX(CENTER_ALIGNMENT);
        sponsorButton.setAlignmentY(CENTER_ALIGNMENT);
        sponsorButton.setBorderPainted(false);
        sponsorButton.setOpaque(false);
        sponsorButton.addActionListener(e -> openURL(sponsorButton.getToolTipText()));
        sponsorPanel.add(sponsorButton);

        this.add(sponsorPanel);

        choiceButtonGroup = new ButtonGroup();

        JPanel choicePanel = new JPanel();
        choicePanel.setLayout(new BoxLayout(choicePanel, BoxLayout.Y_AXIS));
        boolean first = true;
        SelectButtonAction sba = new SelectButtonAction();
        for (Actions action : Actions.values()) {
            if (action == Actions.CLIENT && profile.hideClient()) continue;
            if (action == Actions.SERVER && profile.hideServer()) continue;
            if (action == Actions.EXTRACT && profile.hideExtract()) continue;
            if (action == Actions.OFFLINE && profile.hideOffline()) continue;
            actions.put(action.name(), prog -> action.getAction(profile, prog));
            JRadioButton radioButton = new JRadioButton();
            radioButton.setAction(sba);
            radioButton.setText(action.getButtonLabel());
            radioButton.setActionCommand(action.name());
            radioButton.setToolTipText(action.getTooltip());
            radioButton.setSelected(first);
            radioButton.setAlignmentX(LEFT_ALIGNMENT);
            radioButton.setAlignmentY(CENTER_ALIGNMENT);
            choiceButtonGroup.add(radioButton);
            choicePanel.add(radioButton);
            first = false;
        }

        choicePanel.setAlignmentX(RIGHT_ALIGNMENT);
        choicePanel.setAlignmentY(CENTER_ALIGNMENT);
        add(choicePanel);

        JPanel entryPanel = new JPanel();
        entryPanel.setLayout(new BoxLayout(entryPanel,BoxLayout.X_AXIS));

        this.targetDir = targetDir;
        selectedDirText = new JTextField();
        selectedDirText.setEditable(false);
        selectedDirText.setToolTipText("Path to minecraft");
        selectedDirText.setColumns(30);
//        homeDir.setMaximumSize(homeDir.getPreferredSize());
        entryPanel.add(selectedDirText);
        JButton dirSelect = new JButton();
        dirSelect.setAction(new FileSelectAction());
        dirSelect.setText("...");
        dirSelect.setToolTipText("Select an alternative minecraft directory");
        entryPanel.add(dirSelect);

        entryPanel.setAlignmentX(LEFT_ALIGNMENT);
        entryPanel.setAlignmentY(TOP_ALIGNMENT);
        infoLabel = new JLabel();
        infoLabel.setHorizontalTextPosition(JLabel.LEFT);
        infoLabel.setVerticalTextPosition(JLabel.TOP);
        infoLabel.setAlignmentX(LEFT_ALIGNMENT);
        infoLabel.setAlignmentY(TOP_ALIGNMENT);
        infoLabel.setForeground(Color.RED);
        infoLabel.setVisible(false);

        fileEntryPanel = new JPanel();
        fileEntryPanel.setLayout(new BoxLayout(fileEntryPanel,BoxLayout.Y_AXIS));
        fileEntryPanel.add(infoLabel);
        fileEntryPanel.add(Box.createVerticalGlue());
        fileEntryPanel.add(entryPanel);
        fileEntryPanel.setAlignmentX(CENTER_ALIGNMENT);
        fileEntryPanel.setAlignmentY(TOP_ALIGNMENT);
        this.add(fileEntryPanel);
        updateFilePath();
    }

    private void updateFilePath() {
        try {
            targetDir = targetDir.getCanonicalFile();
            selectedDirText.setText(targetDir.getPath());
        } catch (IOException e) {
            System.out.println("Failed to make cononical file: " + targetDir);
            e.printStackTrace();
        }

        Action action = actions.get(choiceButtonGroup.getSelection().getActionCommand()).apply(null);
        boolean valid = action.isPathValid(targetDir);

        if (profile.getMirror() != null) {
            String message = String.format("<html><a href=\'%s\'>Data kindly mirrored by %s</a></html>", profile.getMirror().getName(), profile.getMirror().getHomepage());
            sponsorButton.setText(message);
            sponsorButton.setToolTipText(profile.getMirror().getHomepage());
            if (profile.getMirror().getImageAddress() != null)
                sponsorButton.setIcon(profile.getMirror().getImage());
            else
                sponsorButton.setIcon(null);
            sponsorPanel.setVisible(true);
        } else {
            sponsorPanel.setVisible(false);
        }

        if (valid) {
            selectedDirText.setForeground(null);
            infoLabel.setVisible(false);
            fileEntryPanel.setBorder(null);
        } else {
            selectedDirText.setForeground(Color.RED);
            fileEntryPanel.setBorder(new LineBorder(Color.RED));
            infoLabel.setText("<html>" + action.getFileError(targetDir) + "</html>");
            infoLabel.setVisible(true);
        }

        if (dialog != null) {
            dialog.invalidate();
            dialog.pack();
        }
    }

    public void run(ProgressCallback monitor) {
        JOptionPane optionPane = new JOptionPane(this, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);

        dialog = optionPane.createDialog("Forge Installer");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
        int result = (Integer) (optionPane.getValue() != null ? optionPane.getValue() : -1);

        if (result == JOptionPane.OK_OPTION) {
            ProgressFrame prog = new ProgressFrame(monitor, "Installing " + profile.getVersion(), Thread.currentThread()::interrupt);
            SimpleInstaller.hookStdOut(prog);
            Action action = actions.get(choiceButtonGroup.getSelection().getActionCommand()).apply(prog);
            try {
                prog.setVisible(true);
                prog.toFront();

                if (action.run(targetDir, installer)) {
                    prog.start("Finished!");
                    prog.progress(1);
                    JOptionPane.showMessageDialog(null, action.getSuccessMessage(), "Complete", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    prog.start("There was an error during installation");
                }
            } catch (ActionCanceledException e) {
                JOptionPane.showMessageDialog(null, "Installation Canceled", "Forge Installer", JOptionPane.WARNING_MESSAGE);
            } catch (Exception e) {
                String message = "There was an exception running task: " + e.toString();
                if (badCerts != null && !badCerts.isEmpty()) {
                    message += "<br>" +
                        "The following addresse did not have valid certificates: " + badCerts + "<br>" +
                        "This typically happens with an outdated java install. Try updating your java install from https://adoptium.net/";
                }
                JOptionPane.showOptionDialog(null, message, "Error", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, new Object[]{"Ok", SwingUtil.createlogButton()}, "");
                e.printStackTrace();
            } finally {
                prog.dispose();
                SimpleInstaller.hookStdOut(monitor);
            }
        }
        dialog.dispose();
    }

    private void openURL(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
            EventQueue.invokeLater(() -> {
                InstallerPanel.this.dialog.toFront();
                InstallerPanel.this.dialog.requestFocus();
            });
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(InstallerPanel.this, "An error occurred launching the browser", "Error launching browser", JOptionPane.ERROR_MESSAGE);
        }
    }
}
