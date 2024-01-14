/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraftforge.installer.actions.Actions;
import net.minecraftforge.installer.actions.OfflineAction;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;

public class SimpleInstaller {
    public static boolean headless = false;
    public static boolean debug = false;
    public static URL mirror = null;

    public static void main(String[] args) throws IOException, URISyntaxException {
        ProgressCallback monitor;
        try {
            monitor = ProgressCallback.withOutputs(System.out, getLog());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            monitor = ProgressCallback.withOutputs(System.out);
        }
        hookStdOut(monitor);

        if (System.getProperty("java.net.preferIPv4Stack") == null) //This is a dirty hack, but screw it, i'm hoping this as default will fix more things then it breaks.
            System.setProperty("java.net.preferIPv4Stack", "true");
        String vendor = System.getProperty("java.vendor", "missing vendor");
        String javaVersion = System.getProperty("java.version", "missing java version");
        String jvmVersion = System.getProperty("java.vm.version", "missing jvm version");
        monitor.message(String.format("JVM info: %s - %s - %s", vendor, javaVersion, jvmVersion));
        monitor.message("java.net.preferIPv4Stack=" + System.getProperty("java.net.preferIPv4Stack"));
        monitor.message("Current Time: " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()));

        File installer = new File(SimpleInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (installer.getAbsolutePath().contains("!/")) {
            monitor.stage("Due to java limitation, please do not run this jar in a folder ending with !");
            monitor.message(installer.getAbsolutePath());
            return;
        }

        OptionParser parser = new OptionParser();
        Map<Actions, OptionSpec<File>> actions = new LinkedHashMap<>();
        actions.put(Actions.SERVER, action(parser, "installServer", "Install a server to the specified directory"));
        actions.put(Actions.CLIENT, action(parser, "installClient", "Install the client files to the specified directory"));
        actions.put(Actions.EXTRACT, action(parser, "extract", "Extract the contained jar file to the specified directory"));
        actions.put(Actions.OFFLINE, action(parser, "makeOffline", "Creates a offline installer at the specified path"));
        OptionSpec<Void> helpOption = parser.acceptsAll(Arrays.asList("h", "help"),"Help with this installer");
        OptionSpec<Void> offlineOption = parser.accepts("offline", "Don't attempt any network calls");
        OptionSpec<Void> debugOption = parser.accepts("debug", "Run in debug mode -- don't delete any files");
        OptionSpec<URL> mirrorOption = parser.accepts("mirror", "Use a specific mirror URL").withRequiredArg().ofType(URL.class);
        OptionSet optionSet = parser.parse(args);

        if (optionSet.has(helpOption)) {
            parser.printHelpOn(System.out);
            return;
        }

        debug = optionSet.has(debugOption);
        if (optionSet.has(mirrorOption))
            mirror = optionSet.valueOf(mirrorOption);

        String badCerts = "";
        if (optionSet.has(offlineOption) || SimpleInstaller.class.getResource("/" + OfflineAction.OFFLINE_FLAG) != null) {
            DownloadUtils.OFFLINE_MODE = true;
            monitor.message("ENABLING OFFLINE MODE");
        } else {
            for(String host : new String[] {
                "files.minecraftforge.net",
                "maven.minecraftforge.net",
                "libraries.minecraft.net",
                "launchermeta.mojang.com",
                "piston-meta.mojang.com",
                "authserver.mojang.com",
            }) {
                monitor.message("Host: " + host + " [" + DownloadUtils.getIps(host).stream().collect(Collectors.joining(", ")) + "]");
            }

            for (String host : new String[] {
                "https://files.minecraftforge.net/",
                "https://launchermeta.mojang.com/"
            }) {
                if (DownloadUtils.checkCertificate(host))
                    continue;
                if (!badCerts.isEmpty())
                    badCerts += ", ";
                 badCerts += host;
            }
        }

        boolean didWork = false;

        for (Actions action : actions.keySet()) {
            OptionSpec<File> option = actions.get(action);
            if (!optionSet.has(option))
                continue;

            try {
                if (!badCerts.isEmpty()) {
                    monitor.message("Failed to validate certificates for " + badCerts + " this typically means you have an outdated java.");
                    monitor.message("If instalation fails try updating your java!");
                    return;
                }

                File target = optionSet.valueOf(option);
                SimpleInstaller.headless = true;
                monitor.message("Target Directory: " + target);
                InstallV1 install = Util.loadInstallProfile();

                if (install.getMirror() != null)
                    monitor.stage(String.format("Data kindly mirrored by %s at %s", install.getMirror().getName(), install.getMirror().getHomepage()));

                if (!action.getAction(install, monitor).run(target, installer)) {
                    monitor.stage("There was an error during installation");
                    System.exit(1);
                } else {
                    monitor.message(action.getSuccess());
                    monitor.stage("You can delete this installer file now if you wish");
                }

                System.exit(0);
            } catch (Throwable e) {
                monitor.stage("A problem installing was detected, install cannot continue");
                System.exit(1);
            }
        }

        if (!didWork)
            launchGui(monitor, installer, badCerts);
    }

    private static OptionSpec<File> action(OptionParser parser, String arg, String desc) {
        return parser.accepts(arg, desc).withOptionalArg().ofType(File.class).defaultsTo(new File("."));
    }

    public static File getMCDir() {
        String userHomeDir = System.getProperty("user.home", ".");
        String osType = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String mcDir = ".minecraft";
        if (osType.contains("win") && System.getenv("APPDATA") != null)
            return new File(System.getenv("APPDATA"), mcDir);
        else if (osType.contains("mac"))
            return new File(userHomeDir, "Library/Application Support/minecraft");
        return new File(userHomeDir, mcDir);
    }

    private static void launchGui(ProgressCallback monitor, File installer, String badCerts) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) { }

        try {
            InstallV1 profile = Util.loadInstallProfile();
            if (profile.getMirror() != null)
                monitor.stage(String.format("Data kindly mirrored by %s at %s", profile.getMirror().getName(), profile.getMirror().getHomepage()));
            InstallerPanel panel = new InstallerPanel(getMCDir(), profile, installer, badCerts);
            panel.run(monitor);
        } catch (Throwable e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                "Something went wrong while installing: " + e.toString() + "\n" +
                "Check log for more details.",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static OutputStream getLog() throws FileNotFoundException {
        File f = new File(SimpleInstaller.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        File output;
        if (f.isFile()) output = new File(f.getName() + ".log");
        else            output = new File("installer.log");

        return new BufferedOutputStream(new FileOutputStream(output));
    }

    static void hookStdOut(ProgressCallback monitor) {
        final Pattern endingWhitespace = Pattern.compile("\\r?\\n$");
        final OutputStream monitorStream = new OutputStream() {
            @Override
            public void write(byte[] buf, int off, int len) {
                byte[] toWrite = new byte[len];
                System.arraycopy(buf, off, toWrite, 0, len);
                write(toWrite);
            }

            @Override
            public void write(byte[] b) {
                String toWrite = new String(b);
                toWrite = endingWhitespace.matcher(toWrite).replaceAll("");
                if (!toWrite.isEmpty())
                    monitor.message(toWrite);
            }

            @Override
            public void write(int b) {
                write(new byte[] { (byte) b });
            }
        };

        System.setOut(new PrintStream(monitorStream));
        System.setErr(new PrintStream(monitorStream));
    }
}
