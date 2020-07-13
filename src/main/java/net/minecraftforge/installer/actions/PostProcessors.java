/*
 * Installer
 * Copyright (c) 2016-2018.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.minecraftforge.installer.actions;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
                log("===============================================================================");

                Map<String, String> outputs = new HashMap<>();
                if (!proc.getOutputs().isEmpty()) {
                    boolean miss = false;
                    log("  Cache: ");
                    for (Entry<String, String> e : proc.getOutputs().entrySet()) {
                        String key = e.getKey();
                        if (key.charAt(0) == '{' && key.charAt(key.length() - 1) == '}')
                            key = data.get(key.substring(1, key.length() - 1));
                        else if (key.charAt(0) == '[' && key.charAt(key.length() - 1) == ']')
                            key = Artifact.from(key.substring(1, key.length() - 1)).getLocalPath(librariesDir).getAbsolutePath();

                        String value = e.getValue();
                        if (value != null) {
                            if (value.charAt(0) == '{' && value.charAt(value.length() - 1) == '}')
                                value = data.get(value.substring(1, value.length() - 1));
                            else if (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'')
                                value = value.substring(1, value.length() - 1);
                        }

                        if (key == null || value == null) {
                            error("  Invalid configuration, bad output config: [" + e.getKey() + ": " + e.getValue() + "]");
                            return false;
                        }

                        outputs.put(key, value);
                        File artifact = new File(key);
                        if (!artifact.exists()) {
                            log("    " + key + " Missing");
                            miss = true;
                        } else {
                            String sha = DownloadUtils.getSha1(artifact);
                            if (sha.equals(value)) {
                                log("    " + key + " Validated: " + value);
                            } else {
                                log("    " + key);
                                log("      Expected: " + value);
                                log("      Actual:   " + sha);
                                miss = true;
                                artifact.delete();
                            }
                        }
                    }
                    if (!miss) {
                        log("  Cache Hit!");
                        continue;
                    }
                }

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

                ClassLoader cl = new URLClassLoader(classpath.toArray(new URL[classpath.size()]), getParentClassloader());
                try {
                    Class<?> cls = Class.forName(mainClass, true, cl);
                    Method main = cls.getDeclaredMethod("main", String[].class);
                    main.invoke(null, (Object)args.toArray(new String[args.size()]));
                } catch (InvocationTargetException ite) {
                    Throwable e = ite.getCause();
                    if (e.getMessage() == null)
                        error("Failed to run processor: " + e.getClass().getName() + "\nSee log for more details.");
                    else
                        error("Failed to run processor: " + e.getClass().getName() + ":" + e.getMessage() + "\nSee log for more details.");
                    return false;
                } catch (Throwable e) {
                    e.printStackTrace();
                    if (e.getMessage() == null)
                        error("Failed to run processor: " + e.getClass().getName() + "\nSee log for more details.");
                    else
                        error("Failed to run processor: " + e.getClass().getName() + ":" + e.getMessage() + "\nSee log for more details.");
                    return false;
                }

                if (!outputs.isEmpty()) {
                    for (Entry<String, String> e : outputs.entrySet()) {
                        File artifact = new File(e.getKey());
                        if (!artifact.exists()) {
                            err.append("\n    ").append(e.getKey()).append(" missing");
                        } else {
                            String sha = DownloadUtils.getSha1(artifact);
                            if (sha.equals(e.getValue())) {
                                log("  Output: " + e.getKey() + " Checksum Validated: " + sha);
                            } else {
                                err.append("\n    ").append(e.getKey())
                                   .append("\n      Expected: ").append(e.getValue())
                                   .append("\n      Actual:   ").append(sha);
                                if (!artifact.delete())
                                    err.append("\n      Could not delete file");
                            }
                        }
                    }
                    if (err.length() > 0) {
                        error("  Processor failed, invalid outputs:" + err.toString());
                        return false;
                    }
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
    private void log(String message) {
        for (String line : message.split("\n"))
            monitor.message(line);
    }

    private static boolean clChecked = false;
    private static ClassLoader parentClassLoader = null;
    @SuppressWarnings("unused")
    private synchronized ClassLoader getParentClassloader() { //Reflectively try and get the platform classloader, done this way to prevent hard dep on J9.
        if (!clChecked) {
            clChecked = true;
            if (!System.getProperty("java.version").startsWith("1.")) { //in 9+ the changed from 1.8 to just 9. So this essentially detects if we're <9
                try {
                    Method getPlatform = ClassLoader.class.getDeclaredMethod("getPlatformClassLoader");
                    parentClassLoader = (ClassLoader)getPlatform.invoke(null);
                } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    log("No platform classloader: " + System.getProperty("java.version"));
                }
            }
        }
        return parentClassLoader;
    }
}
