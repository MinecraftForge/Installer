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
import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.actions.ProgressCallback.MessagePriority;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.Install.Processor;
import net.minecraftforge.installer.json.Version.Library;

public class PostProcessors {
    private final Install profile;
    private final boolean isClient;
    private final ProgressCallback monitor;
    private final boolean hasTasks;
    private final Map<String, String> data;
    private final List<Processor> processors;

    public PostProcessors(Install profile, boolean isClient, ProgressCallback monitor) {
        this.profile = profile;
        this.isClient = isClient;
        this.monitor = monitor;
        this.processors = profile.getProcessors(isClient ? "client" : "server");
        this.hasTasks = !this.processors.isEmpty();
        this.data = profile.getData(isClient);
    }


    public Library[] getLibraries() {
        return hasTasks ? profile.getLibraries() : new Library[0];
    }

    public int getTaskCount() {
        return hasTasks ? 0 :
            profile.getLibraries().length +
            processors.size() +
            profile.getData(isClient).size();
    }

    public boolean process(File librariesDir, File minecraft) {
        try {
            if (!data.isEmpty()) {
                StringBuilder err = new StringBuilder();
                Path temp  = Files.createTempDirectory("forge_installer");
                monitor.start("Created Temporary Directory: " + temp);
                double steps = data.size();
                int progress = 1;
                for (String key : data.keySet()) {
                    monitor.progress(progress++ / steps);
                    String value = data.get(key);

                    if (value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') { //Artifact
                        data.put(key, Artifact.from(value.substring(1, value.length() -1)).getLocalPath(librariesDir).getAbsolutePath());
                    } else if (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') { //Literal
                        data.put(key, value.substring(1, value.length() -1));
                    } else {
                        File target = Paths.get(temp.toString(), value).toFile();
                        monitor.message("  Extracting: " + value);
                        if (!DownloadUtils.extractFile(value, target))
                            err.append("\n  ").append(value);
                        data.put(key, target.getAbsolutePath());
                    }
                }
                if (err.length() > 0) {
                    error("Failed to extract files from archive: " + err.toString());
                    return false;
                }
            }
            data.put("SIDE", isClient ? "client" : "server");
            data.put("MINECRAFT_JAR", minecraft.getAbsolutePath());

            int progress = 1;
            if (processors.size() == 1) {
                monitor.stage("Building Processor");
            } else {
                monitor.start("Building Processors");
            }
            for (Processor proc : processors) {
                monitor.progress((double) progress++ / processors.size());

                File jar = proc.getJar().getLocalPath(librariesDir);
                if (!jar.exists() || !jar.isFile()) {
                    error("  Missing Jar for processor: " + jar.getAbsolutePath());
                    return false;
                }

                // Locate main class in jar file
                JarFile jarFile = new JarFile(jar);
                String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                jarFile.close();

                if (mainClass == null || mainClass.isEmpty()) {
                    error("  Jar does not have main class: " + jar.getAbsolutePath());
                    return false;
                }
                monitor.message("  MainClass: " + mainClass, MessagePriority.LOW);

                List<URL> classpath = new ArrayList<>();
                StringBuilder err = new StringBuilder();
                monitor.message("  Classpath:", MessagePriority.LOW);
                monitor.message("    " + jar.getAbsolutePath(), MessagePriority.LOW);
                classpath.add(jar.toURI().toURL());
                for (Artifact dep : proc.getClasspath()) {
                    File lib = dep.getLocalPath(librariesDir);
                    if (!lib.exists() || !lib.isFile())
                        err.append("\n  ").append(dep.getDescriptor());
                    classpath.add(lib.toURI().toURL());
                    monitor.message("    " + lib.getAbsolutePath(), MessagePriority.LOW);
                }
                if (err.length() > 0) {
                    error("  Missing Processor dependancies: " + err.toString());
                    return false;
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
                    return false;
                }
                monitor.message("  Args: " + args.stream().map(a -> a.indexOf(' ') != -1 || a.indexOf(',') != -1 ? '"' + a + '"' : a).collect(Collectors.joining(", ")), MessagePriority.LOW);

                ClassLoader cl = new URLClassLoader(classpath.toArray(new URL[classpath.size()]), null);
                try {
                    Class<?> cls = Class.forName(mainClass, true, cl);
                    Method main = cls.getDeclaredMethod("main", String[].class);
                    main.invoke(null, (Object)args.toArray(new String[args.size()]));
                } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
                    e.printStackTrace();
                    error("Failed to run processor: " + e.getMessage() + "\nSee log for more details.");
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void error(String message) {
        if (!SimpleInstaller.headless)
            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        for (String line : message.split("\n"))
            monitor.message(line);
    }
}
