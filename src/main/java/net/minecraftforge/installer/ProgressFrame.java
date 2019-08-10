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

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import net.minecraftforge.installer.actions.ProgressCallback;

public class ProgressFrame extends JFrame implements ProgressCallback
{
    private static final long serialVersionUID = 1L;
    
    private final ProgressCallback parent;

    private final JPanel panel = new JPanel();

    private final JLabel progressText;
    private final JProgressBar progressBar;
    private final JTextArea consoleArea;

    public ProgressFrame(ProgressCallback parent, String title, Runnable canceler)
    {
        this.parent = parent;
        
        setResizable(false);
        setTitle(title);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 600, 400);
        setContentPane(panel);
        setLocationRelativeTo(null);

        GridBagLayout gridBagLayout = new GridBagLayout();
        gridBagLayout.columnWidths = new int[] { 600, 0 };
        gridBagLayout.rowHeights = new int[] {0, 0, 0, 200};
        gridBagLayout.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
        gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 1.0 };
        panel.setLayout(gridBagLayout);

        progressText = new JLabel("Progress Text");
        GridBagConstraints gbc_lblProgressText = new GridBagConstraints();
        gbc_lblProgressText.insets = new Insets(10, 0, 5, 0);
        gbc_lblProgressText.gridx = 0;
        gbc_lblProgressText.gridy = 0;
        panel.add(progressText, gbc_lblProgressText);

        progressBar = new JProgressBar();
        GridBagConstraints gbc_progressBar = new GridBagConstraints();
        gbc_progressBar.insets = new Insets(0, 25, 5, 25);
        gbc_progressBar.fill = GridBagConstraints.HORIZONTAL;
        gbc_progressBar.gridx = 0;
        gbc_progressBar.gridy = 1;
        panel.add(progressBar, gbc_progressBar);

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e ->
        {
            canceler.run();
            ProgressFrame.this.dispose();
        });
        GridBagConstraints gbc_btnCancel = new GridBagConstraints();
        gbc_btnCancel.insets = new Insets(0, 25, 5, 25);
        gbc_btnCancel.fill = GridBagConstraints.HORIZONTAL;
        gbc_btnCancel.gridx = 0;
        gbc_btnCancel.gridy = 2;
        panel.add(btnCancel, gbc_btnCancel);
        
        consoleArea = new JTextArea();
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        GridBagConstraints gbc_textArea = new GridBagConstraints();
        gbc_textArea.insets = new Insets(15, 25, 25, 25);
        gbc_textArea.fill = GridBagConstraints.BOTH;
        gbc_textArea.gridx = 0;
        gbc_textArea.gridy = 3;
        
        JScrollPane scroll = new JScrollPane(consoleArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setAutoscrolls(true);
        panel.add(scroll, gbc_textArea);
    }

    @Override
    public void start(String label)
    {
        message(label, MessagePriority.HIGH, false);
        this.progressBar.setValue(0);
        this.progressBar.setIndeterminate(false);
        parent.start(label);
    }

    @Override
    public void progress(double progress)
    {
        this.progressBar.setValue((int) (progress * 100));
        parent.progress(progress);
    }

    @Override
    public void stage(String message)
    {
        message(message, MessagePriority.HIGH, false);
        this.progressBar.setIndeterminate(true);
        parent.stage(message);
    }

    @Override
    public void message(String message, MessagePriority priority)
    {
        message(message, priority, true);
    }

    public void message(String message, MessagePriority priority, boolean notifyParent)
    {
        if (priority == MessagePriority.HIGH)
        {
            this.progressText.setText(message);
        }
        consoleArea.append(message + "\n");
        consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        if (notifyParent)
            parent.message(message, priority);
    }
}
