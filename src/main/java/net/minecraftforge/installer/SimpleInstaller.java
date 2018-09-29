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
import java.util.regex.Pattern;

import javax.swing.UIManager;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.installer.actions.Actions;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.Util;

public class SimpleInstaller
{
    public static boolean headless = false;

    public static void main(String[] args) throws IOException
    {
        ProgressCallback monitor;
        try
        {
            monitor = ProgressCallback.withOutputs(System.out, getLog());
            hookStdOut(monitor);
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
            monitor = ProgressCallback.withOutputs(System.out);
        }
        
        if (System.getProperty("java.net.preferIPv4Stack") == null) //This is a dirty hack, but screw it, i'm hoping this as default will fix more things then it breaks.
        {
            System.setProperty("java.net.preferIPv4Stack", "true");
        }
        monitor.message("java.net.preferIPv4Stack=" + System.getProperty("java.net.preferIPv4Stack"));

        String path = SimpleInstaller.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path.contains("!/"))
        {
            monitor.stage("Due to java limitation, please do not run this jar in a folder ending with !");
            monitor.message(path);
            return;
        }

        OptionParser parser = new OptionParser();
        OptionSpec<File> serverInstallOption = parser.accepts("installServer", "Install a server to the current directory").withOptionalArg().ofType(File.class).defaultsTo(new File("."));
        OptionSpec<File> clientInstallOption = parser.accepts("installClient", "Install the client to the specified directory, defaults to normal minecraft install.").withOptionalArg().ofType(File.class).defaultsTo(getMCDir());
        OptionSpec<File> extractOption = parser.accepts("extract", "Extract the contained jar file to the specified directory").withOptionalArg().ofType(File.class).defaultsTo(new File("."));
        OptionSpec<Void> helpOption = parser.acceptsAll(Arrays.asList("h", "help"),"Help with this installer");
        OptionSpec<Void> offlineOption = parser.accepts("offline", "Don't attempt any network calls");
        OptionSet optionSet = parser.parse(args);

        if (optionSet.has(helpOption)) {
            parser.printHelpOn(System.out);
            return;
        }

        int cnt = 0;
        if (optionSet.has(offlineOption))
        {
            DownloadUtils.OFFLINE_MODE = true;
            monitor.message("ENABELING OFFLINE MODE");
            cnt = 1;
        }

        Actions action = null;
        File target = null;
        if (optionSet.has(serverInstallOption)) {
            action = Actions.SERVER;
            target = optionSet.valueOf(serverInstallOption);
        } else if (optionSet.has(clientInstallOption)) {
            action = Actions.CLIENT;
            target = optionSet.valueOf(clientInstallOption);
        } else if (optionSet.has(extractOption)) {
            action = Actions.EXTRACT;
            target = optionSet.valueOf(extractOption);
        }

        if (action != null)
        {
            try
            {
                SimpleInstaller.headless = true;
                monitor.message("Target Directory: " + target);
                Install install = Util.loadInstallProfile();
                if (!action.getAction(install, monitor).run(target, a -> true))
                {
                    monitor.stage("There was an error during installation");
                    System.exit(1);
                }
                else
                {
                    monitor.message(action.getSuccess(install.getPath().getName()));
                    monitor.stage("You can delete this installer file now if you wish");
                }
                System.exit(0);
            }
            catch (Throwable e)
            {
                monitor.stage("A problem installing was detected, install cannot continue");
                System.exit(1);
            }
        }
        else if (optionSet.specs().size() > cnt)
            parser.printHelpOn(System.err);
        else
            launchGui(monitor);
    }

    private static File getMCDir()
    {
        String userHomeDir = System.getProperty("user.home", ".");
        String osType = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String mcDir = ".minecraft";
        if (osType.contains("win") && System.getenv("APPDATA") != null)
            return new File(System.getenv("APPDATA"), mcDir);
        else if (osType.contains("mac"))
            return new File(new File(new File(userHomeDir, "Library"),"Application Support"),"minecraft");
        return new File(userHomeDir, mcDir);
    }

    private static void launchGui(ProgressCallback monitor)
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e)
        {
        }

        Install profile = Util.loadInstallProfile();
        InstallerPanel panel = new InstallerPanel(getMCDir(), profile);
        panel.run(monitor);
    }

    private static OutputStream getLog() throws FileNotFoundException
    {
        File f = new File(SimpleInstaller.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        File output;
        if (f.isFile()) output = new File(f.getName() + ".log");
        else            output = new File("installer.log");

        return new BufferedOutputStream(new FileOutputStream(output));
    }
    
    static void hookStdOut(ProgressCallback monitor)
    {
        final Pattern endingWhitespace = Pattern.compile("\\r?\\n$");
        final OutputStream monitorStream = new OutputStream() {

            @Override
            public void write(byte[] buf, int off, int len)
            {
                byte[] toWrite = new byte[len];
                System.arraycopy(buf, off, toWrite, 0, len);
                write(toWrite);
            }
            
            @Override
            public void write(byte[] b)
            {
                String toWrite = new String(b);
                toWrite = endingWhitespace.matcher(toWrite).replaceAll("");
                if (!toWrite.isEmpty()) {
                    monitor.message(toWrite);
                }
            }
            
            @Override
            public void write(int b)
            {
                write(new byte[] { (byte) b });
            }
        };
        
        System.setOut(new PrintStream(monitorStream));
        System.setErr(new PrintStream(monitorStream));
    }
}
