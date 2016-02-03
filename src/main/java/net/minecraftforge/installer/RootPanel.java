package net.minecraftforge.installer;

import javax.swing.JPanel;

import com.alee.extended.filechooser.FilesSelectionListener;
import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.extended.image.WebImage;
import com.alee.extended.label.WebMultiLineLabel;
import com.alee.extended.layout.VerticalFlowLayout;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebButtonGroup;
import com.alee.extended.progress.WebProgressOverlay;
import com.alee.extended.window.WebPopOver;
import com.alee.laf.button.WebButton;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotification;
import com.alee.managers.popup.PopupWay;
import com.alee.managers.popup.WebButtonPopup;
import com.alee.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

import argo.format.PrettyJsonFormatter;
import argo.jdom.JdomParser;
import argo.jdom.JsonNode;
import argo.jdom.JsonNodeFactories;
import argo.jdom.JsonRootNode;
import argo.jdom.JsonStringNode;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import javax.swing.JFrame;
import javax.swing.JLabel;
import java.awt.Insets;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.awt.BorderLayout;
import java.awt.CardLayout;

import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JButton;

/**
 * 
 * @author ArgonBird18
 *
 */
public class RootPanel extends JPanel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 4408645689141318663L;
	private JFrame frame = new JFrame("Forge Installer");
	private WebFileChooserField fileField;
	private WebMultiLineLabel lblStatus;
	private final DefaultComboBoxModel<String> profileListModel = new DefaultComboBoxModel<String>();
	private CustomWebFileDrop modsFileDrop;
	private File modsDir;

	/**
	 * Create the panel.
	 * 
	 * I used the Eclipse WindowBuilder plugin to do
	 * the GridBagLayout
	 */
	public RootPanel(File targetDir) {
		
		// Status Label
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{1.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 1.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		BufferedImage image;
        try {
            image = ImageIO.read(SimpleInstaller.class.getResourceAsStream(VersionInfo.getLogoFileName()));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        WebImage logo = new WebImage(image);
        logo.setMargin(10, 5, 5, 5);
		GridBagConstraints gbc_logo = new GridBagConstraints();
		gbc_logo.insets = new Insets(0, 0, 5, 0);
		gbc_logo.gridx = 0;
		gbc_logo.gridy = 0;
		add(logo, gbc_logo);
		
		// Status Label
		
		lblStatus = new WebMultiLineLabel("");
		lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
		GridBagConstraints gbc_lblStatus = new GridBagConstraints();
		gbc_lblStatus.insets = new Insets(0, 0, 5, 0);
		gbc_lblStatus.gridx = 0;
		gbc_lblStatus.gridy = 1;
		add(lblStatus, gbc_lblStatus);
		
		// Card Layout
		
		CardLayout cards = new CardLayout();
		JPanel cardPanel = new JPanel(cards);
		GridBagConstraints gbc_cardPanel = new GridBagConstraints();
		gbc_cardPanel.insets = new Insets(0, 0, 5, 0);
		gbc_cardPanel.fill = GridBagConstraints.BOTH;
		gbc_cardPanel.gridx = 0;
		gbc_cardPanel.gridy = 2;
		
		add(cardPanel, gbc_cardPanel);
		
		// Install Panel
		
		{
			lblStatus.setText("Forge is not installed. Forge must be installed before any mods can be installed.");
			
			JPanel installPanel = new JPanel();
			cardPanel.add(installPanel, "install");
			
			GridBagLayout gbl_installPanel = new GridBagLayout();
			gbl_installPanel.columnWidths = new int[]{0, 0};
			gbl_installPanel.rowHeights = new int[]{0, 0, 0, 0};
			gbl_installPanel.columnWeights = new double[]{1.0, Double.MIN_VALUE};
			gbl_installPanel.rowWeights = new double[]{0.0, 0.0, 0.0, Double.MIN_VALUE};
			installPanel.setLayout(gbl_installPanel);
			
			final JComboBox<String> typeBox = new JComboBox<String>();
			typeBox.setModel(new DefaultComboBoxModel<String>(new String[] {
					"For Client", "For Server", "Extract"}));
			GridBagConstraints gbc_typeBox = new GridBagConstraints();
			gbc_typeBox.insets = new Insets(0, 0, 5, 0);
			gbc_typeBox.gridx = 0;
			gbc_typeBox.gridy = 0;
			installPanel.add(typeBox, gbc_typeBox);
			
	        final WebProgressOverlay progressOverlay = new WebProgressOverlay();
	        progressOverlay.setConsumeEvents(false);
			
			WebButton btnInstall = new WebButton("Install");
			btnInstall.setRound(9);
			btnInstall.setMargin(0, 5, 0, 5);
			progressOverlay.setComponent(btnInstall);
			btnInstall.addActionListener(new ActionListener() {
				
				public void actionPerformed(ActionEvent e) {
					if(progressOverlay.isShowLoad() || progressOverlay.isConsumeEvents())
						return;
					btnInstall.setText("Installing...");
					progressOverlay.setShowLoad(true);
					progressOverlay.setConsumeEvents(true);
					new SwingWorker<Void,Boolean>(){
						InstallerAction action;

						@Override
						protected Void doInBackground() throws Exception {
							action = InstallerAction.EXTRACT;
							File file = fileField.getSelectedFiles().get(0);
							
							if(typeBox.getSelectedIndex() == 0){
								action = InstallerAction.CLIENT;
							} else if(typeBox.getSelectedIndex() == 1){
								action = InstallerAction.SERVER;
								JFileChooser chooser = new JFileChooser();
								chooser.setMultiSelectionEnabled(false);
								chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
								chooser.setDialogTitle("Select the installation location");
								if(chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION){
									file = chooser.getSelectedFile();
								}else{
									publish(false);
									return null;
								}
							}else{
								JFileChooser chooser = new JFileChooser();
								chooser.setMultiSelectionEnabled(false);
								chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
								chooser.setDialogTitle("Select the extraction location");
								if(chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION){
									file = chooser.getSelectedFile();
								}else{
									publish(false);
									return null;
								}
							}
							if(action.run(file)){
								publish(true);
								return null;
							}
							publish(false);
							return null;
						}
						
						@Override
						protected void process(List<Boolean> chunks){
							if(chunks.get(0)){
								final WebNotification notificationPopup = new WebNotification();
				                notificationPopup.setIcon(NotificationIcon.information);
				                notificationPopup.setDisplayTime(5000);
				                notificationPopup.setContent(action.getSuccessMessage());
				                NotificationManager.showNotification(notificationPopup);
				                cards.show(cardPanel, "mods");
							}
							btnInstall.setText("Install");
							progressOverlay.setShowLoad(false);
							progressOverlay.setConsumeEvents(false);
						}
						
					}.execute();

				}
				
			});
			GridBagConstraints gbc_btnInstall = new GridBagConstraints();
			gbc_btnInstall.gridx = 0;
			gbc_btnInstall.gridy = 2;
			installPanel.add(progressOverlay, gbc_btnInstall);
		}
		
		// Add Mods Panel
		
		{
			modsDir = new File(targetDir,"mods");
			lblStatus.setText("Forge is installed. Now start adding some mods! "
					+ "First select a Forge profile or create one. Profiles keep "
					+ "normal Minecraft worlds seperate from modded ones. Then drag "
					+ "mods into the panel to add them. When your done, select your "
					+ "profile in the launcher, then click play.");
			
			JPanel addModsPanel = new JPanel();
			cardPanel.add(addModsPanel, "mods");
			
			addModsPanel.setLayout(new BorderLayout(0, 0));
			
			JPanel northPanel = new JPanel();
			addModsPanel.add(northPanel, BorderLayout.NORTH);
			northPanel.setLayout(new BorderLayout(0, 0));
			
			JComboBox<String> profileBox = new JComboBox<String>(profileListModel);
			profileBox.addItemListener(new ItemListener(){

				@Override
				public void itemStateChanged(ItemEvent e) {
					if(profileBox.getSelectedIndex() < 0)
						return;
					try{
						String profile = (String) profileBox.getSelectedItem();
						InputStream installProfile = new File(fileField.getSelectedFiles().get(0),
								"launcher_profiles.json").toURI().toURL().openStream();
				        JdomParser parser = new JdomParser();
				        JsonRootNode root = parser.parse(new InputStreamReader(installProfile, Charsets.UTF_8));
				        File file = fileField.getSelectedFiles().get(0);
				        try{
				        	file = new File(root.getNode("profiles").getNode(profile).getNode("gameDir").getText());
				        }catch(Exception ex){}
				        modsDir = new File(file,"mods");
				        modsFileDrop.removeAllSelectedFiles();
				        if(modsDir.exists() && modsDir.isDirectory()){
				        	if(modsDir.listFiles() != null)
								modsFileDrop.addSelectedFiles(modsDir.listFiles());
				        }else{
				        	modsDir.mkdirs();
				        }
					}catch(Exception ex){
						ex.printStackTrace();
					}
					
				}
				
			});
			northPanel.add(profileBox, BorderLayout.CENTER);
			
			WebButton btnAdd = new WebButton("Add");
			WebButtonPopup addPopup = new WebButtonPopup(btnAdd, PopupWay.downLeft);
			{
				JTextField nameField = new JTextField("Forge "+VersionInfo.getMinecraftVersion(), 10);
				JButton addButton = new JButton("Add");
				addButton.addActionListener(new ActionListener(){

					@Override
					public void actionPerformed(ActionEvent e) {
						addPopup.hidePopup();
						// It doesn't make sense to put this in a SwingWorker
						// It takes so little time...on most computers :)
						try{
							InputStream installProfile = new File(fileField.getSelectedFiles().get(0),
									"launcher_profiles.json").toURI().toURL().openStream();
					        JdomParser parser = new JdomParser();
					        JsonRootNode root = parser.parse(new InputStreamReader(installProfile, Charsets.UTF_8));
					        try{
					        	if(root.getNode("profiles").getNode(nameField.getText()) != null){
						        	final WebPopOver popOver = new WebPopOver(frame);
					                popOver.setCloseOnFocusLoss(true);
					                popOver.setMargin(10);
					                popOver.setLayout(new VerticalFlowLayout());
					                popOver.add(new JLabel("A profile with that name already exists!"));
					                popOver.show(btnAdd);
					                return;
						        }
					        }catch(Exception ex){}
					        JsonRootNode newNode = JsonNodeFactories.object(
					        		JsonNodeFactories.field("name", JsonNodeFactories.string(nameField.getText())),
					        		JsonNodeFactories.field("lastVersionId", JsonNodeFactories.string(VersionInfo.getVersionTarget())),
					        		JsonNodeFactories.field("gameDir", JsonNodeFactories.string(getProfileGameDir(nameField.getText()).getAbsolutePath())));
					        HashMap<JsonStringNode, JsonNode> newNodes = new HashMap<JsonStringNode, JsonNode>();
					        newNodes.putAll(root.getNode("profiles").getFields());
					        newNodes.put(JsonNodeFactories.string(nameField.getText()),newNode);
					        
					        HashMap<JsonStringNode, JsonNode> finalMap = new HashMap<JsonStringNode, JsonNode>();
					        finalMap.putAll(root.getFields());
					        finalMap.put(JsonNodeFactories.string("profiles"), JsonNodeFactories.object(newNodes));
					        
					        BufferedWriter newWriter = Files.newWriter(new File(fileField.
					        		getSelectedFiles().get(0), "launcher_profiles.json"), Charsets.UTF_8);
					        PrettyJsonFormatter.fieldOrderPreservingPrettyJsonFormatter().format(JsonNodeFactories.object(finalMap), newWriter); 
					        newWriter.close();
					        
					        final WebPopOver popOver = new WebPopOver(frame);
			                popOver.setCloseOnFocusLoss(true);
			                popOver.setMargin(10);
			                popOver.setLayout(new VerticalFlowLayout());
			                popOver.add(new JLabel("Profile added!"));
			                popOver.show(btnAdd);
						}catch(Exception ex){
							ex.printStackTrace();
							final WebPopOver popOver = new WebPopOver(frame);
			                popOver.setCloseOnFocusLoss(true);
			                popOver.setMargin(10);
			                popOver.setLayout(new VerticalFlowLayout());
			                popOver.add(new JLabel("Uh-oh. Something went wrong!"));
			                popOver.add(new JLabel("Are the launcher profiles write protected?"));
			                popOver.show(btnAdd);
						}
						updateList();
						profileBox.setSelectedItem(nameField.getText());
						
					}
					
				});
				addPopup.setContent(new GroupPanel(false, 
						new JLabel("Profile Name:"), nameField, addButton));
			}
			
			WebButton btnRemove = new WebButton("Remove");
			WebButtonPopup removePopup = new WebButtonPopup(btnRemove, PopupWay.downLeft);
			{
				JButton removeButton = new JButton("Remove");
				removeButton.addActionListener(new ActionListener(){

					@Override
					public void actionPerformed(ActionEvent e) {
						removePopup.hidePopup();
						if(profileBox.getSelectedIndex() < 0){
							final WebPopOver popOver = new WebPopOver(frame);
			                popOver.setCloseOnFocusLoss(true);
			                popOver.setMargin(10);
			                popOver.setLayout(new VerticalFlowLayout());
			                popOver.add(new JLabel("Select a profile first!"));
			                popOver.show(btnRemove);
			                return;
						}
						try{
							InputStream installProfile = new File(fileField.getSelectedFiles().get(0),
									"launcher_profiles.json").toURI().toURL().openStream();
					        JdomParser parser = new JdomParser();
					        JsonRootNode root = parser.parse(new InputStreamReader(installProfile, Charsets.UTF_8));
					        HashMap<JsonStringNode, JsonNode> newNodes = new HashMap<JsonStringNode, JsonNode>();
					        newNodes.putAll(root.getNode("profiles").getFields());
					        newNodes.remove(JsonNodeFactories.string((String) profileBox.getSelectedItem()));
					        
					        HashMap<JsonStringNode, JsonNode> finalMap = new HashMap<JsonStringNode, JsonNode>();
					        finalMap.putAll(root.getFields());
					        finalMap.put(JsonNodeFactories.string("profiles"), JsonNodeFactories.object(newNodes));
					        
					        BufferedWriter newWriter = Files.newWriter(new File(fileField.
					        		getSelectedFiles().get(0), "launcher_profiles.json"), Charsets.UTF_8);
					        PrettyJsonFormatter.fieldOrderPreservingPrettyJsonFormatter().format(JsonNodeFactories.object(finalMap), newWriter); 
					        newWriter.close();
						}catch(Exception ex){
							ex.printStackTrace();
							final WebPopOver popOver = new WebPopOver(frame);
			                popOver.setCloseOnFocusLoss(true);
			                popOver.setMargin(10);
			                popOver.setLayout(new VerticalFlowLayout());
			                popOver.add(new JLabel("Uh-oh. Something went wrong!"));
			                popOver.add(new JLabel("Are the launcher profiles write protected?"));
			                popOver.show(btnRemove);
						}
						updateList();
						
					}
					
				});
				removePopup.setContent(new GroupPanel(false, 
						new JLabel("WARNING!!! This profile will be"), new JLabel("gone FOREVER (a very long time)"),
						removeButton));
			}
			northPanel.add(new WebButtonGroup(true, btnAdd, btnRemove), BorderLayout.EAST);
			
			JScrollPane scrollPane = new JScrollPane();
			modsFileDrop = new CustomWebFileDrop();
			modsFileDrop.setDropText("Drop .jar or .zip mods to add them");
			modsFileDrop.removeAll();
			if(modsDir.listFiles() != null)
				modsFileDrop.addSelectedFiles(modsDir.listFiles());
			
			modsFileDrop.addUIFileSelectionListener(new FilesSelectionListener(){
				
				private List<File> lastList = Arrays.asList(modsDir.listFiles());

				@Override
				public void selectionChanged(List<File> arg0) {
					ArrayList<File> removed = new ArrayList<File>();
					removed.addAll(lastList);
					removed.removeAll(arg0);
					lastList = arg0;
					
					new SwingWorker<Void,Void>(){

						@Override
						protected Void doInBackground() throws Exception {
							for(File file : arg0){
								if(!file.getParentFile().equals(modsDir)){
									FileUtils.copyFile(file, new File(modsDir,file.getName()));
								}
							}
							for(File file : removed){
								// Now lets check to be sure we delete the right file...
								if(!file.getParentFile().equals(modsDir)){
									file = new File(modsDir,file.getName());
								}
								System.out.println(file.getAbsolutePath());
								file.delete();
							}
							return null;
						}
						
					}.execute();
					
				}
				
			});
			scrollPane.setViewportView(modsFileDrop);
			addModsPanel.add(scrollPane, BorderLayout.CENTER);
			
			profileBox.addItemListener(new ItemListener(){

				@Override
				public void itemStateChanged(ItemEvent e) {
					profileBox.getSelectedItem();
					
				}
				
			});
		}
		
		{
			File versionTarget = new File(new File(targetDir,"versions"),VersionInfo.getVersionTarget());
					if(versionTarget.exists() && versionTarget.isDirectory())
						cards.show(cardPanel, "mods");
					else
						cards.show(cardPanel, "install");
		}
		
		// Advanced button
		
		JPanel advancedButtonContainer = new JPanel();
		GridBagConstraints gbc_advancedButtonContainer = new GridBagConstraints();
		gbc_advancedButtonContainer.insets = new Insets(0, 0, 5, 0);
		gbc_advancedButtonContainer.fill = GridBagConstraints.BOTH;
		gbc_advancedButtonContainer.gridx = 0;
		gbc_advancedButtonContainer.gridy = 3;
		add(advancedButtonContainer, gbc_advancedButtonContainer);
		advancedButtonContainer.setLayout(new BorderLayout(0, 0));
		
		WebButton btnAdvanced = new WebButton("Advanced");
		WebButtonPopup advancedPopup = new WebButtonPopup(btnAdvanced, PopupWay.upLeft);
		
		{
			// Advanced Panel
			fileField = new WebFileChooserField(frame);
			fileField.setPreferredWidth(200);
			fileField.setMultiSelectionEnabled(false);
			fileField.setShowFileShortName(false);
			fileField.setShowRemoveButton(false);
			fileField.setSelectedFile(targetDir);
			advancedPopup.setContent(new GroupPanel(5, false, 
					new JLabel("Minecraft Location:"), fileField,
					new JLabel("Version:"+VersionInfo.getVersionTarget())));
		}
		
		advancedButtonContainer.add(btnAdvanced, BorderLayout.EAST);

	}
	
	private void updateList(){
		try{
			InputStream installProfile = new File(fileField.getSelectedFiles().get(0),
					"launcher_profiles.json").toURI().toURL().openStream();
	        JdomParser parser = new JdomParser();
	        JsonRootNode root = parser.parse(new InputStreamReader(installProfile, Charsets.UTF_8));
	        Set<JsonStringNode> keySet = root.getNode("profiles").getFields().keySet();
	        profileListModel.removeAllElements();
	        for(JsonStringNode node : keySet){
	        	profileListModel.addElement(node.getText());
	        }
		}catch(Exception ex){
			ex.printStackTrace();
			ActionType.error("Failed to reload the profile list");
		}
	}
	
	public static File getProfileGameDir(String name){
		String osType = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
		String userHomeDir = System.getProperty("user.home", ".");
		File targetDir = null;
		String mcDir = ".minecraft"+name.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
		if (osType.contains("win") && System.getenv("APPDATA") != null){
			targetDir = new File(System.getenv("APPDATA"), mcDir);
		}else if (osType.contains("mac")){
			targetDir = new File(new File(new File(userHomeDir, "Library"),"Application Support"),
					"minecraft"+name.replaceAll("[^a-zA-Z0-9_\\-\\.]", "_"));
		}else{
			targetDir = new File(userHomeDir, mcDir);
		}
		return targetDir;
	}

	public void run() {
		frame.setContentPane(this);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);
		frame.setSize(400,500);
		frame.setVisible(true);
		updateList();
	}

}