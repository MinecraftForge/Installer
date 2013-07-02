package cpw.mods.fml.installer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;


public class SimpleInstaller {
    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException
    {
        String userHomeDir = System.getProperty("user.home", ".");
        String osType = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        File targetDir = null;
        String mcDir = ".minecraft";
        if (osType.contains("win") && System.getenv("APPDATA") != null)
        {
            targetDir = new File(System.getenv("APPDATA"), mcDir);
        }
        else if (osType.contains("mac"))
        {
            targetDir = new File(new File(new File(userHomeDir, "Library"),"Application Support"),"minecraft");
        }
        else
        {
            targetDir = new File(userHomeDir, mcDir);
        }

        try
        {
            VersionInfo.getVersionTarget();
        }
        catch (Throwable e)
        {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Corrupt download detected, cannot install", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        InstallerPanel panel = new InstallerPanel(targetDir);
        panel.run();
    }

}
