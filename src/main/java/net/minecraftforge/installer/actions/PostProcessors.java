/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.actions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;
import javax.swing.JOptionPane;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.actions.ProgressCallback.MessagePriority;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Install.Processor;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.SwingUtil;
import net.minecraftforge.installer.json.Version.Library;
import net.minecraftforge.installer.json.Util;

public class PostProcessors {
    private final InstallV1 profile;
    private final boolean isClient;
    private final ProgressCallback monitor;
    private final boolean hasTasks;
    private final List<Processor> processors;

    public PostProcessors(InstallV1 profile, boolean isClient, ProgressCallback monitor) {
        this.profile = profile;
        this.isClient = isClient;
        this.monitor = monitor;
        this.processors = profile.getProcessors(isClient ? "client" : "server");
        this.hasTasks = !this.processors.isEmpty();
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

    public Set<File> process(File librariesDir, File minecraft, File root, File installer) {
        try {
            Map<String, DataEntry> data = loadData(librariesDir);
            if (data == null)
                return null;

            data.put("SIDE",              new DataEntry(isClient ? "client" : "server"));
            data.put("MINECRAFT_JAR",     new FileEntry(minecraft));
            data.put("MINECRAFT_VERSION", new DataEntry(profile.getMinecraft()));
            data.put("ROOT",              new FileEntry(root));
            data.put("INSTALLER",         new FileEntry(installer));
            data.put("LIBRARY_DIR",       new FileEntry(librariesDir));

            if (processors.size() == 1)
                monitor.stage("Building Processor");
            else
                monitor.start("Building Processors");

            List<List<Output>> allOutputs = buildOutputs(librariesDir, data, processors);
            if (allOutputs == null)
                return null;

            String libPrefix = librariesDir.getAbsolutePath().replace('\\', '/');
            if (!libPrefix.endsWith("/"))
                libPrefix += '/';

            Set<File> ret = new HashSet<>();
            for (int x = 0; x < processors.size(); x++) {
                monitor.progress((double)(x + 1) / processors.size());
                log("===============================================================================");
                Processor proc = processors.get(x);
                List<Output> outputs = allOutputs.get(x);

                if (!outputs.isEmpty()) {
                    boolean miss = false;
                    log("  Cache: ");
                    for (Output output : outputs) {
                        if (!output.file.exists()) {
                            log("    " + output.file + " Missing");

                            String path = output.file.getAbsolutePath().replace('\\', '/');
                            if (!path.startsWith(libPrefix)) {
                                miss = true;
                            } else {
                                String relative = "/cache/" + path.substring(libPrefix.length());
                                try (final InputStream input = DownloadUtils.class.getResourceAsStream(relative)) {
                                    if (input != null) {
                                        log("    Extracting output from " + relative);
                                        if (!output.file.getParentFile().exists())
                                             output.file.getParentFile().mkdirs();

                                        Files.copy(input, output.file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                        String sha1 = DownloadUtils.getSha1(output.file);
                                        if (output.sha1.equals(sha1)) {
                                            log("      Extraction completed: Checksum validated.");
                                            ret.add(output.file);
                                        } else {
                                            log("    " + output.file);
                                            log("      Expected: " + output.sha1);
                                            log("      Actual:   " + sha1);
                                            miss = true;
                                            output.file.delete();
                                        }
                                    } else {
                                        miss = true;
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    return null;
                                }
                            }
                        } else {
                            String sha = DownloadUtils.getSha1(output.file);
                            if (sha.equals(output.sha1)) {
                                log("    " + output.file + " Validated: " + output.sha1);
                                ret.add(output.file);
                            } else {
                                log("    " + output.file);
                                log("      Expected: " + output.sha1);
                                log("      Actual:   " + sha);
                                miss = true;
                                output.file.delete();
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
                    return null;
                }

                // Locate main class in jar file
                JarFile jarFile = new JarFile(jar);
                String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                jarFile.close();

                if (mainClass == null || mainClass.isEmpty()) {
                    error("  Jar does not have main class: " + jar.getAbsolutePath());
                    return null;
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
                    error("  Missing Processor Dependencies: " + err.toString());
                    return null;
                }

                List<String> args = new ArrayList<>();
                for (String arg : proc.getArgs()) {
                    char start = arg.charAt(0);
                    char end = arg.charAt(arg.length() - 1);

                    if (start == '[' && end == ']') //Library
                        args.add(Artifact.from(arg.substring(1, arg.length() - 1)).getLocalPath(librariesDir).getAbsolutePath());
                    else
                        args.add(Util.replaceTokens(data, arg));
                }
                if (err.length() > 0) {
                    error("  Missing Processor data values: " + err.toString());
                    return null;
                }
                monitor.message("  Args: " + args.stream().map(a -> a.indexOf(' ') != -1 || a.indexOf(',') != -1 ? '"' + a + '"' : a).collect(Collectors.joining(", ")), MessagePriority.LOW);

                ClassLoader cl = new URLClassLoader(classpath.toArray(new URL[classpath.size()]), getParentClassloader());
                // Set the thread context classloader to be our newly constructed one so that service loaders work
                Thread currentThread = Thread.currentThread();
                ClassLoader threadClassloader = currentThread.getContextClassLoader();
                currentThread.setContextClassLoader(cl);
                try {
                    Class<?> cls = Class.forName(mainClass, true, cl);
                    Method main = cls.getDeclaredMethod("main", String[].class);
                    main.invoke(null, (Object)args.toArray(new String[args.size()]));
                } catch (InvocationTargetException ite) {
                    Throwable e = ite.getCause();
                    handleError(e);
                    return null;
                } catch (Throwable e) {
                    handleError(e);
                    return null;
                } finally {
                    // Set back to the previous classloader
                    currentThread.setContextClassLoader(threadClassloader);
                }

                if (!outputs.isEmpty()) {
                    for (Output output : outputs) {
                        ret.add(output.file);
                        if (!output.file.exists()) {
                            err.append("\n    ").append(output.file).append(" missing");
                        } else {
                            String sha = DownloadUtils.getSha1(output.file);
                            if (sha.equals(output.sha1)) {
                                log("  Output: " + output.file + " Checksum Validated: " + sha);
                            } else {
                                err.append("\n    ").append(output.file)
                                   .append("\n      Expected: ").append(output.sha1)
                                   .append("\n      Actual:   ").append(sha);
                                if (!SimpleInstaller.debug && !output.file.delete())
                                    err.append("\n      Could not delete file");
                            }
                        }
                    }
                    if (err.length() > 0) {
                        error("  Processor failed, invalid outputs:" + err.toString());
                        return null;
                    }
                }
            }

            return ret;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void handleError(Throwable e) {
        e.printStackTrace();
        StringBuilder buf = new StringBuilder();
        buf.append("Failed to run processor: ").append(e.getClass().getName());
        if (e.getMessage() != null)
            buf.append(':').append(e.getMessage());
        if (e instanceof SSLException) {
            buf.append("\nThis is a SSL Exception, this might be caused by you having an outdated java install.")
                .append("\nTry updating your java before trying again.");
        }
        buf.append("\nSee log for more details");
        error(buf.toString());
        if (e.getMessage() == null)
            error("Failed to run processor: " + e.getClass().getName() + "\nSee log for more details.");
        else
            error("Failed to run processor: " + e.getClass().getName() + ":" + e.getMessage() + "\nSee log for more details.");

    }

    private void error(String message) {
        if (!SimpleInstaller.headless)
            JOptionPane.showOptionDialog(null, message, "Error", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, new Object[]{"Ok", SwingUtil.createLogButton()}, "");
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

    private Map<String, DataEntry> loadData(File librariesDir) throws IOException {
        Map<String, String> cfg = profile.getData(isClient);
        if (cfg.isEmpty())
            return new HashMap<>();

        Map<String, DataEntry> ret = new HashMap<>();

        StringBuilder err = new StringBuilder();
        Path temp  = Files.createTempDirectory("forge_installer");
        monitor.start("Created Temporary Directory: " + temp);

        double steps = cfg.size();
        int progress = 1;
        for (String key : cfg.keySet()) {
            monitor.progress(progress++ / steps);
            String value = cfg.get(key);

            DataEntry entry = null;
            if (value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') { //Artifact
                Artifact artifact = Artifact.from(value.substring(1, value.length() - 1));
                entry = new ArtifactEntry(artifact, librariesDir);
            } else if (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') { //Literal
                entry = new DataEntry(value.substring(1, value.length() - 1));
            } else {
                File target = Paths.get(temp.toString(), value).toFile();
                monitor.message("  Extracting: " + value);
                if (!DownloadUtils.extractFile(value, target))
                    err.append("\n  ").append(value);

                entry = new FileEntry(target);
            }
            ret.put(key, entry);
        }

        if (err.length() > 0) {
            error("Failed to extract files from archive: " + err.toString());
            return null;
        }

        return ret;
    }

    private List<List<Output>> buildOutputs(File librariesDir, Map<String, DataEntry> data, List<Processor> processors) {
        List<List<Output>> ret = new ArrayList<>();
        for (Processor proc : processors) {
            Map<String, String> outputs = proc.getOutputs();
            if (outputs.isEmpty()) {
                ret.add(Collections.emptyList());
                continue;
            }

            List<Output> pout = new ArrayList<>();
            for (String key : outputs.keySet()) {
                char start = key.charAt(0);
                char end = key.charAt(key.length() - 1);

                String file = null;
                if (start == '[' && end == ']')
                    file = Artifact.from(key.substring(1, key.length() - 1)).getLocalPath(librariesDir).getAbsolutePath();
                else
                    file = Util.replaceTokens(data, key);

                String value = outputs.get(key);
                if (value != null)
                    value = Util.replaceTokens(data, value);

                if (key == null || value == null) {
                    error("  Invalid configuration, bad output config: [" + key + ": " + value + "]");
                    return null;
                }

                pout.add(new Output(new File(file), value));
            }
            ret.add(pout);
        }
        return ret;
    }

    private static class DataEntry implements Supplier<String> {
        protected final String value;
        protected DataEntry(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public String get() {
            return toString();
        }
    }

    private static class FileEntry extends DataEntry {
        @SuppressWarnings("unused")
        private final File file;
        protected FileEntry(File file) {
            super(file.getAbsolutePath());
            this.file = file;
        }
    }

    private static class ArtifactEntry extends DataEntry {
        @SuppressWarnings("unused")
        private final Artifact artifact;
        protected ArtifactEntry(Artifact artifact, File root) {
            super(artifact.getLocalPath(root).getAbsolutePath());
            this.artifact = artifact;
        }
    }

    private static class Output {
        private final File file;
        private final String sha1;

        private Output(File file, String sha1) {
            this.file = file;
            this.sha1 = sha1;
        }
    }
}
