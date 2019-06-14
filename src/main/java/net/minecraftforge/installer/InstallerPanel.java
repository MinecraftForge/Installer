/*
 * Installer
 * Copyright (c) 2016-2018.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;

import net.minecraftforge.installer.actions.Action;
import net.minecraftforge.installer.actions.ActionCanceledException;
import net.minecraftforge.installer.actions.Actions;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.OptionalLibrary;
import net.minecraftforge.installer.json.Util;

@SuppressWarnings("unused")
public class InstallerPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private File targetDir;
    private ButtonGroup choiceButtonGroup;
    private JTextField selectedDirText;
    private JLabel infoLabel;
    private JButton sponsorButton;
    private JDialog dialog;
    //private JLabel sponsorLogo;
    private JPanel sponsorPanel;
    private JPanel fileEntryPanel;
    private List<OptionalListEntry> optionals = new ArrayList<>();
    private Install profile;
    private Map<String, Function<ProgressCallback, Action>> actions = new HashMap<>();

    private class FileSelectAction extends AbstractAction
    {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e)
        {
            JFileChooser dirChooser = new JFileChooser();
            dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            dirChooser.setFileHidingEnabled(false);
            dirChooser.ensureFileIsVisible(targetDir);
            dirChooser.setSelectedFile(targetDir);
            int response = dirChooser.showOpenDialog(InstallerPanel.this);
            switch (response)
            {
            case JFileChooser.APPROVE_OPTION:
                targetDir = dirChooser.getSelectedFile();
                updateFilePath();
                break;
            default:
                break;
            }
        }
    }

    private class SelectButtonAction extends AbstractAction
    {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e)
        {
            updateFilePath();
        }

    }

    private static final String URL = "89504E470D0A1A0A0000000D4948445200000014000000160803000000F79F4C3400000012504C5445FFFFFFCCFFFF9999996666663333330000009E8B9AE70000000274524E53FF00E5B7304A000000564944415478016DCB410E003108425169E5FE579E98584246593EBF8165C24C5C614BED08455ECABC947929F392584A12CD8021EBEF91B0BD46A13969682BCC45E3706AE04E0DE0E42C819FA3D10F10BE954DC4C4DE07EB6A0497D14F4E8F0000000049454E44AE426082";
    public static byte[] hexToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private BufferedImage getImage(String path, String default_)
    {
        try
        {
            InputStream in = SimpleInstaller.class.getResourceAsStream(path);
            if (in == null && default_ != null)
                in = new ByteArrayInputStream(hexToByteArray(default_));
            return ImageIO.read(in);
        }
        catch (IOException e)
        {
            if (default_ == null)
                throw new RuntimeException(e);
            else
                return new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        }
    }

    public InstallerPanel(File targetDir, Install profile)
    {
        this.profile = profile;

        if (this.profile.getSpec() != 0) {
            JOptionPane.showMessageDialog(null, "Invalid launcher profile spec: " + profile.getSpec() + " Only 0 is supported, Whoever package this installer messed up.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        BufferedImage image = getImage(profile.getLogo(), null);
        //final BufferedImage urlIcon = getImage(profile.getUrlIcon(), URL);

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

//        sponsorLogo = new JLabel();
//        sponsorLogo.setSize(50, 20);
//        sponsorLogo.setAlignmentX(CENTER_ALIGNMENT);
//        sponsorLogo.setAlignmentY(CENTER_ALIGNMENT);
//        sponsorPanel.add(sponsorLogo);

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
        for (Actions action : Actions.values())
        {
            if (action == Actions.CLIENT && profile.hideClient()) continue;
            if (action == Actions.SERVER && profile.hideServer()) continue;
            if (action == Actions.EXTRACT && profile.hideExtract()) continue;
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

        /*
        if (VersionInfo.hasOptionals())
        {
            optionals = new OptionalListEntry[VersionInfo.getOptionals().size()];
            int x = 0;
            for (OptionalLibrary opt : VersionInfo.getOptionals())
                optionals[x++] = new OptionalListEntry(opt);

            final JList<OptionalListEntry> list = new JList<OptionalListEntry>(optionals);

            list.setCellRenderer(new ListCellRenderer<OptionalListEntry>()
            {
                private JPanel panel = new JPanel(new BorderLayout());
                private JCheckBox check = new JCheckBox();
                private JLabel icon = new JLabel(new ImageIcon(urlIcon));
                {
                    check.setHorizontalAlignment(SwingConstants.LEFT);
                    icon.setSize(urlIcon.getWidth(), urlIcon.getHeight());
                    panel.add(check, BorderLayout.LINE_START);
                    panel.add(icon,  BorderLayout.LINE_END);
                }

                @Override
                public Component getListCellRendererComponent(JList<? extends OptionalListEntry> list, OptionalListEntry value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    check.setSelected(value.isEnabled());
                    check.setText(value.lib.getName());
                    icon.setVisible(value.lib.getURL() != null);
                    return panel;
                }
            });

            list.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent event)
                {
                    int index = list.locationToIndex(event.getPoint());
                    OptionalListEntry entry = list.getModel().getElementAt(index);

                    if (entry.lib.getURL() != null && event.getPoint().getX() > list.getWidth() - urlIcon.getWidth())
                        openURL(entry.lib.getURL());
                    else
                        entry.setEnabled(!entry.isEnabled());
                    list.repaint(list.getCellBounds(index, index));
                }
            });
            list.addMouseMotionListener(new MouseMotionListener()
            {
                public void mouseMoved(MouseEvent event)
                {
                    int index = list.locationToIndex(event.getPoint());
                    OptionalListEntry entry = list.getModel().getElementAt(index);
                    if (entry.lib.getDesc() != null)
                    {
                        StringBuilder tt = new StringBuilder();
                        tt.append("<html>");
                        //tt.append("  <h1>").append(index).append(" ").append(entry.lib.getName()).append("</h1>");
                        //if (entry.lib.getURL() != null)
                        //    tt.append("  URL: <a href=\"").append(entry.lib.getURL()).append("\">").append(entry.lib.getURL()).append("</a><br />");
                        if (entry.lib.getDesc() != null)
                            tt.append(entry.lib.getDesc());
                        tt.append("</html>");
                        list.setToolTipText(tt.toString());
                    }
                    else
                        list.setToolTipText(null);
                }

                @Override public void mouseDragged(MouseEvent event) {}
            });


            add(new JScrollPane(list));
        }
        */

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


    private void updateFilePath()
    {
        try
        {
            targetDir = targetDir.getCanonicalFile();
            selectedDirText.setText(targetDir.getPath());
        }
        catch (IOException e)
        {

        }

        Action action = actions.get(choiceButtonGroup.getSelection().getActionCommand()).apply(null);
        boolean valid = action.isPathValid(targetDir);

        if (profile.getMirror() != null)
        {
            sponsorButton.setText(action.getSponsorMessage());
            sponsorButton.setToolTipText(profile.getMirror().getHomepage());
            if (profile.getMirror().getImageAddress() != null)
                sponsorButton.setIcon(profile.getMirror().getImage());
            else
                sponsorButton.setIcon(null);
            sponsorPanel.setVisible(true);
        }
        else
        {
            sponsorPanel.setVisible(false);
        }
        if (valid)
        {
            selectedDirText.setForeground(null);
            infoLabel.setVisible(false);
            fileEntryPanel.setBorder(null);
        }
        else
        {
            selectedDirText.setForeground(Color.RED);
            fileEntryPanel.setBorder(new LineBorder(Color.RED));
            infoLabel.setText("<html>"+action.getFileError(targetDir)+"</html>");
            infoLabel.setVisible(true);
        }
        if (dialog!=null)
        {
            dialog.invalidate();
            dialog.pack();
        }
    }

    public void run(ProgressCallback monitor)
    {
        JOptionPane optionPane = new JOptionPane(this, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);

        Frame emptyFrame = new Frame("Mod system installer");
        emptyFrame.setLocationRelativeTo(null);
        emptyFrame.setUndecorated(true);
        emptyFrame.setVisible(true);
        dialog = optionPane.createDialog(emptyFrame, "Mod system installer");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
        int result = (Integer) (optionPane.getValue() != null ? optionPane.getValue() : -1);
        if (result == JOptionPane.OK_OPTION)
        {
            ProgressFrame prog = new ProgressFrame(monitor, "Installing " + profile.getVersion(), Thread.currentThread()::interrupt);
            SimpleInstaller.hookStdOut(prog);
            Predicate<String> optPred = input -> {
                Optional<OptionalListEntry> ent = this.optionals.stream().filter(e -> e.lib.getArtifact().equals(input)).findFirst();
                return !ent.isPresent() || ent.get().isEnabled();
            };
            Action action = actions.get(choiceButtonGroup.getSelection().getActionCommand()).apply(prog);
            try {
                prog.setVisible(true);
                prog.toFront();
                if (action.run(targetDir, optPred)) {
                    prog.start("Finished!");
                    prog.progress(1);
                    JOptionPane.showMessageDialog(null, action.getSuccessMessage(), "Complete", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (ActionCanceledException e) {
                JOptionPane.showMessageDialog(null, "Installation Canceled", "Forge Installer", JOptionPane.WARNING_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "There was an exception running task: " + e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            } finally {
                prog.dispose();
                SimpleInstaller.hookStdOut(monitor);
            }
        }
        dialog.dispose();
        emptyFrame.dispose();
    }

    private void openURL(String url)
    {
        try
        {
            Desktop.getDesktop().browse(new URI(url));
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run()
                {
                    InstallerPanel.this.dialog.toFront();
                    InstallerPanel.this.dialog.requestFocus();
                }
            });
        }
        catch (Exception ex)
        {
            JOptionPane.showMessageDialog(InstallerPanel.this, "An error occurred launching the browser", "Error launching browser", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class OptionalListEntry
    {
        OptionalLibrary lib;
        private boolean enabled = false;

        OptionalListEntry(OptionalLibrary lib)
        {
            this.lib = lib;
            this.enabled = lib.getDefault();
        }

        public boolean isEnabled(){ return this.enabled; }
        public void setEnabled(boolean v){ this.enabled = v; }
    }
}
