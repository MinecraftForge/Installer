package net.minecraftforge.installer.actions;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.IMonitor;
import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.Install.Processor;
import net.minecraftforge.installer.json.Version.Library;

public class PostProcessors {
    private final Install profile;
    private final boolean isClient;
    private final IMonitor monitor;
    private final boolean hasTasks;
    private final Map<String, String> data;

    public PostProcessors(Install profile, boolean isClient, IMonitor monitor) {
        this.profile = profile;
        this.isClient = isClient;
        this.monitor = monitor;
        this.hasTasks = profile.getProcessors().length > 0;
        this.data = profile.getData(isClient);
    }


    public Library[] getLibraries() {
        return hasTasks ? profile.getLibraries() : new Library[0];
    }

    public int getTaskCount() {
        return hasTasks ? 0 :
            profile.getLibraries().length +
            profile.getProcessors().length +
            profile.getData(isClient).size();
    }

    public int process(int progress, File librariesDir, File minecraft) {
        try {
            if (!data.isEmpty()) {
                StringBuilder err = new StringBuilder();
                Path temp  = Files.createTempDirectory("forge_installer");
                info("Created Temporary Directory: " + temp);
                for (String key : data.keySet()) {
                    monitor.setProgress(progress++);
                    String value = data.get(key);
                    File target = Paths.get(temp.toString(), value).toFile();
                    info("  Extracting: " + value);
                    if (!DownloadUtils.extractFile(value, target))
                        err.append("\n  ").append(value);
                    data.put(key, target.getAbsolutePath());
                }
                if (err.length() > 0) {
                    error("Failed to extract files from archive: " + err.toString());
                    return -1;
                }
            }
            data.put("SIDE", isClient ? "client" : "server");
            data.put("MINECRAFT_JAR", minecraft.getAbsolutePath());

            for (Processor proc : profile.getProcessors()) {
                monitor.setProgress(progress++);
                info("Building Processor:");

                File jar = proc.getJar().getLocalPath(librariesDir);
                if (!jar.exists() || !jar.isFile()) {
                    error("  Missing Jar for processor: " + jar.getAbsolutePath());
                    return -1;
                }

                // Locate main class in jar file
                JarFile jarFile = new JarFile(jar);
                String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                jarFile.close();

                if (mainClass == null || mainClass.isEmpty()) {
                    error("  Jar does not have main class: " + jar.getAbsolutePath());
                    return -1;
                }
                info("  MainClass: " + mainClass);

                List<URL> classpath = new ArrayList<>();
                StringBuilder err = new StringBuilder();
                info("  Classpath:");
                info("    " + jar.getAbsolutePath());
                classpath.add(jar.toURI().toURL());
                for (Artifact dep : proc.getClasspath()) {
                    File lib = dep.getLocalPath(librariesDir);
                    if (!lib.exists() || !lib.isFile())
                        err.append("\n  ").append(dep.getDescriptor());
                    classpath.add(lib.toURI().toURL());
                    info("    " + lib.getAbsolutePath());
                }
                if (err.length() > 0) {
                    error("  Missing Processor dependancies: " + err.toString());
                    return -1;
                }

                List<String> args = new ArrayList<>();
                for (String arg : proc.getArgs()) {
                    char start = arg.charAt(0);
                    char end = arg.charAt(arg.length() - 1);

                    if (start == '[' && end == ']') //Library
                        args.add(Artifact.from(arg.substring(1, arg.length() - 1)).getLocalPath(librariesDir).getAbsolutePath());
                    else if (start == '{' && end == '}') { // Data
                        String key = arg.substring(1, arg.length() - 1);
                        String value = data.get(key);
                        if (value == null)
                            err.append("\n  ").append(key);
                        args.add(value);
                    } else
                        args.add(arg);
                }
                if (err.length() > 0) {
                    error("  Missing Processor data values: " + err.toString());
                    return -1;
                }
                info("  Args: " + args.stream().map(a -> a.indexOf(' ') != -1 || a.indexOf(',') != -1 ? '"' + a + '"' : a).collect(Collectors.joining(", ")));

                ClassLoader cl = new URLClassLoader(classpath.toArray(new URL[classpath.size()]), null);
                try {
                    Class<?> cls = Class.forName(mainClass, true, cl);
                    Method main = cls.getDeclaredMethod("main", String[].class);
                    main.invoke(null, (Object)args.toArray(new String[args.size()]));
                } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
                    e.printStackTrace();
                    error("Failed to run processor: " + e.getMessage() + "\nSee log for more details.");
                    return -1;
                }
            }

            return progress;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private void error(String message) {
        if (!SimpleInstaller.headless)
            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        for (String line : message.split("\n"))
            monitor.setNote(line);
    }

    private void info(String message) {
        for (String line : message.split("\n"))
            monitor.setNote(line);
    }
}
