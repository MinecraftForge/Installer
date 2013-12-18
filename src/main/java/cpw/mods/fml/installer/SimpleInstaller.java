package cpw.mods.fml.installer;

import static cpw.mods.fml.installer.LogHandler.log;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import javax.swing.UIManager;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;

public class SimpleInstaller {
    
	/**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException
    {
        OptionParser parser = new OptionParser();
        OptionSpecBuilder serverInstallOption = parser.accepts("installServer", "Install a server to the current directory");
        OptionSpecBuilder extractOption = parser.accepts("extract", "Extract the contained jar file");
        OptionSpecBuilder helpOption = parser.acceptsAll(Arrays.asList("h", "help"),"Help with this installer");
        OptionSet optionSet = parser.parse(args);
        
        LogHandler.initLogger();
        
        if (optionSet.specs().size()>0)
        {
            handleOptions(parser, optionSet, serverInstallOption, extractOption, helpOption);
        }
        else
        {
            launchGui();
        }
    }

    private static void handleOptions(OptionParser parser, OptionSet optionSet, OptionSpecBuilder serverInstallOption, OptionSpecBuilder extractOption, OptionSpecBuilder helpOption) throws IOException
    {
        if (optionSet.has(serverInstallOption))
        {
            try
            {
                VersionInfo.getVersionTarget();
                ServerInstall.headless = true;
                log.info("Installing server to current directory");
                if (!InstallerAction.SERVER.run(new File(".")))
                {
                	log.severe("There was an error during server installation");
                    System.exit(1);
                }
                else
                {
                	log.info("The server installed successfully, you should now be able to run the file "+VersionInfo.getContainedFile());
                	log.info("You can delete this installer file now if you wish");
                }
                System.exit(0);
            }
            catch (Throwable e)
            {
            	log.severe("A problem installing the server was detected, server install cannot continue");
                System.exit(1);
            }
        }
        else if (optionSet.has(extractOption))
        {
            try
            {
                VersionInfo.getVersionTarget();
                if (!InstallerAction.EXTRACT.run(new File(".")))
                {
                    log.severe("A problem occurred extracting the file to "+VersionInfo.getContainedFile());
                    System.exit(1);
                }
                else
                {
                    log.info("File extracted successfully to "+VersionInfo.getContainedFile());
                    log.info("You can delete this installer file now if you wish");
                }
                System.exit(0);
            }
            catch (Throwable e)
            {
                log.severe("A problem extracting the file was detected, extraction failed");
                System.exit(1);
            }
        }
        else
        {
            parser.printHelpOn(System.err);
        }
    }

    private static void launchGui()
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
            LogHandler.logErrorWithDialog("Corrupt download detected, cannot install", "Error");
            return;
        }

        try
        {
    		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e)
		{
		}

        InstallerPanel panel = new InstallerPanel(targetDir);
        panel.run();
    }

}
