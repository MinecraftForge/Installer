package net.minecraftforge.installer;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

import javax.swing.JOptionPane;
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
        setupLogger();
        OptionParser parser = new OptionParser();
        OptionSpecBuilder serverInstallOption = parser.accepts("installServer", "Install a server to the current directory");
        OptionSpecBuilder extractOption = parser.accepts("extract", "Extract the contained jar file");
        OptionSpecBuilder helpOption = parser.acceptsAll(Arrays.asList("h", "help"),"Help with this installer");
        OptionSet optionSet = parser.parse(args);

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
        String path = VersionInfo.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path.contains("!/"))
        {
            System.out.println("Due to java limitation, please do not run this jar in a folder ending with !");
            System.out.println(path);
            return;
        }

        if (optionSet.has(serverInstallOption))
        {
            try
            {
                VersionInfo.getVersionTarget();
                ServerInstall.headless = true;
                System.out.println("Installing server to current directory");
                if (!InstallerAction.SERVER.run(new File(".")))
                {
                    System.err.println("There was an error during server installation");
                    System.exit(1);
                }
                else
                {
                    System.out.println("The server installed successfully, you should now be able to run the file "+VersionInfo.getContainedFile());
                    System.out.println("You can delete this installer file now if you wish");
                }
                System.exit(0);
            }
            catch (Throwable e)
            {
                System.err.println("A problem installing the server was detected, server install cannot continue");
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
                    System.err.println("A problem occurred extracting the file to "+VersionInfo.getContainedFile());
                    System.exit(1);
                }
                else
                {
                    System.out.println("File extracted successfully to "+VersionInfo.getContainedFile());
                    System.out.println("You can delete this installer file now if you wish");
                }
                System.exit(0);
            }
            catch (Throwable e)
            {
                System.err.println("A problem extracting the file was detected, extraction failed");
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

        String path = VersionInfo.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path.contains("!/"))
        {
            JOptionPane.showMessageDialog(null, "Due to java limitation, please do not run this jar in a folder ending with ! : \n"+ path, "Error", JOptionPane.ERROR_MESSAGE);
            return;
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

    private static void setupLogger()
    {
        File f = new File(VersionInfo.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        File output;
        if (f.isFile()) output = new File(f.getName() + ".log");
        else            output = new File("installer.log");

        try
        {
            System.out.println("Setting up logger: " + output.getAbsolutePath());
            OutputStream fout = new BufferedOutputStream(new FileOutputStream(output));
            System.setOut(new PrintStream(new MultiOutputStream(System.out, fout), true));
            System.setErr(new PrintStream(new MultiOutputStream(System.err, fout), true));
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
            //We errored out, lets just continue on and hope the rest runs fine.
        }
    }

    private static class MultiOutputStream extends OutputStream
    {
        OutputStream[] outs;
        MultiOutputStream(OutputStream... outs)
        {
            this.outs = outs;
        }

        @Override
        public void write(int b) throws IOException
        {
            for (OutputStream out : outs)
                out.write(b);
        }

        @Override
        public void write(byte b[]) throws IOException
        {
            for (OutputStream out : outs)
                out.write(b);
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException
        {
            for (OutputStream out : outs)
                out.write(b, off, len);
        }

        @Override
        public void flush() throws IOException
        {
            for (OutputStream out : outs)
                out.flush();
        }

        @Override
        public void close() throws IOException
        {
            for (OutputStream out : outs)
                out.close();
        }
    }

}
