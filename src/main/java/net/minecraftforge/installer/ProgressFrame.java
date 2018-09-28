package net.minecraftforge.installer;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;

import net.minecraftforge.installer.actions.ProgressCallback;

public class ProgressFrame extends JFrame implements ProgressCallback {
	private static final long serialVersionUID = 1L;

	private final JPanel panel = new JPanel();
	
	private final JLabel progressText;
	private final JProgressBar progressBar;
	
	public ProgressFrame(String title, Runnable canceler) {		
        setResizable(false);
        setTitle(title);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 600, 150);
        setContentPane(panel);
        setLocationRelativeTo(null);
        
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{600, 0};
		gridBagLayout.rowHeights = new int[] {0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0};
		panel.setLayout(gridBagLayout);
		
		progressText = new JLabel("Progress Text");
		GridBagConstraints gbc_lblProgressText = new GridBagConstraints();
		gbc_lblProgressText.insets = new Insets(0, 0, 5, 0);
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
		btnCancel.addActionListener(e -> {
			canceler.run();
			ProgressFrame.this.dispose();
		});
		GridBagConstraints gbc_btnCancel = new GridBagConstraints();
		gbc_btnCancel.insets = new Insets(0, 25, 0, 25);
		gbc_btnCancel.fill = GridBagConstraints.HORIZONTAL;
		gbc_btnCancel.gridx = 0;
		gbc_btnCancel.gridy = 2;
		panel.add(btnCancel, gbc_btnCancel);
	}

	@Override
	public void start(String label) {
		message(label);
		this.progressBar.setValue(0);
		this.progressBar.setIndeterminate(false);
	}
	
	@Override
	public void progress(double progress) {
		this.progressBar.setValue((int) (progress * 100));
	}

	@Override
	public void stage(String message) {
		message(message);
		this.progressBar.setIndeterminate(true);
	}
	
	@Override
	public void message(String message, MessagePriority priority) {
		if (priority != MessagePriority.LOW) {
			this.progressText.setText(message);
		} else {
			System.out.println(message);
		}
	}
}
