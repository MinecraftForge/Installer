package net.minecraftforge.installer;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Locale;

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
        setupLogger();
        if (System.getProperty("java.net.preferIPv4Stack") == null) //This is a dirty hack, but screw it, i'm hoping this as default will fix more things then it breaks.
        {
            System.setProperty("java.net.preferIPv4Stack", "true");
        }
        System.out.println("java.net.preferIPv4Stack=" + System.getProperty("java.net.preferIPv4Stack"));

        String path = SimpleInstaller.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path.contains("!/"))
        {
            System.out.println("Due to java limitation, please do not run this jar in a folder ending with !");
            System.out.println(path);
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
            System.out.println("ENABELING OFFLINE MODE");
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
                System.out.println("Target Directory: " + target);
                Install install = Util.loadInstallProfile();
                if (!action.getAction(install, new ProgressCallback(){}).run(target, a -> true))
                {
                    System.err.println("There was an error during installation");
                    System.exit(1);
                }
                else
                {
                    System.out.println(action.getSuccess(install.getPath().getName()));
                    System.out.println("You can delete this installer file now if you wish");
                }
                System.exit(0);
            }
            catch (Throwable e)
            {
                System.err.println("A problem installing was detected, install cannot continue");
                System.exit(1);
            }
        }
        else if (optionSet.specs().size() > cnt)
            parser.printHelpOn(System.err);
        else
            launchGui();
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

    private static void launchGui()
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
        panel.run();
    }

    private static void setupLogger()
    {
        File f = new File(SimpleInstaller.class.getProtectionDomain().getCodeSource().getLocation().getFile());
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
