/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.actions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version.Library;
import net.minecraftforge.installer.json.Version.LibraryDownload;

public class OfflineAction extends Action {
    public static final String OFFLINE_FLAG = "_FORCE_OFFLINE_INSTALLER_";
    private final File base = findInstallerBase();
    private final PostProcessors processorsClient;
    private final PostProcessors processorsServer;
    private int added = 0;

    protected OfflineAction(InstallV1 profile, ProgressCallback monitor) {
        super(profile, monitor, true);
        this.processorsClient = new PostProcessors(profile, true, monitor);
        this.processorsServer = new PostProcessors(profile, false, monitor);
    }

    @Override
    public boolean run(File target, File installer) throws ActionCanceledException {
        File librariesDir = new File(target, "libraries");
        if (!target.exists())
            target.mkdirs();
        librariesDir.mkdir();
        checkCancel();

        // Download Libraries
        List<File> libDirs = new ArrayList<>();
        File mcLibDir = new File(SimpleInstaller.getMCDir(), "libraries");
        if (mcLibDir.exists())
            libDirs.add(mcLibDir);

        if (!downloadLibraries(librariesDir, libDirs))
            return false;

        // Download client jar file
        File clientTarget = new File(target, "client.jar");
        File serverTarget = new File(target, "server.jar");
        if (!downloadVanilla(clientTarget, "client") || !downloadVanilla(serverTarget, "server"))
            return false;

        // Run processors
        Set<File> outputs = new HashSet<>();
        if (!process(outputs, this.processorsClient, librariesDir, clientTarget, target, base) ||
            !process(outputs, this.processorsServer, librariesDir, serverTarget, target, base))
            return false;

        monitor.message("Building offline installer");
        monitor.message("Found Base: " + base);
        target = cleanTarget(target);
        monitor.message("Output: " + target);

        checkCancel();
        try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(target))) {
            Set<String> seen = new HashSet<>();

            // Copy our input installer jar
            monitor.message("Copying Base Installer Archive");
            try (ZipInputStream zin = new ZipInputStream(new FileInputStream(base))) {
                for (ZipEntry entry; (entry = zin.getNextEntry()) != null; ) {
                    zout.putNextEntry(getNewEntry(entry.getName()));
                    copy(zin, zout);
                    if (entry.getName().startsWith("maven/") && !entry.isDirectory())
                        seen.add(entry.getName().substring(6));
                }
            }

            // Add extra libraries we downloaded
            for (Library lib : getLibraries()) {
                Artifact artifact = lib.getName();
                if (!seen.add(artifact.getPath()))
                    continue;

                File local = artifact.getLocalPath(librariesDir);

                LibraryDownload download = lib.getDownloads() == null ? null :  lib.getDownloads().getArtifact();
                if (download == null) {
                    download = new LibraryDownload();
                    download.setPath(artifact.getPath());
                }

                if (!local.exists()) {
                    if (download.getUrl() == null || download.getUrl().isEmpty()) {
                        monitor.message("Skipping " + artifact.getDescriptor() + " as it's missing its URL, this is probably generated by the installer");
                        continue;
                    } else {
                        error("Library: " + artifact.getDescriptor() + " does not exist, but it should of been downloaded..");
                        monitor.message("Local Path: " + local.getAbsolutePath());
                        return false;
                    }
                }

                monitor.message("Adding: maven/" + artifact.getPath());
                zout.putNextEntry(getNewEntry("maven/" + artifact.getPath()));
                copy(local, zout);
                added++;
            }

            // Add Vanilla files
            monitor.message("Adding: cache/vanilla/client.jar");
            zout.putNextEntry(getNewEntry("cache/vanilla/client.jar"));
            copy(clientTarget, zout);
            monitor.message("Adding: cache/vanilla/server.jar");
            zout.putNextEntry(getNewEntry("cache/vanilla/server.jar"));
            copy(serverTarget, zout);

            // Add Processor outputs, which could be downloaded
            String libPrefix = librariesDir.getAbsolutePath().replace('\\', '/');
            if (!libPrefix.endsWith("/"))
                libPrefix += '/';

            for (File output : outputs) {
                String path = output.getAbsolutePath().replace('\\', '/');
                if (!path.startsWith(libPrefix)) {
                    monitor.message("Skipping " + path);
                    continue;
                }

                String relative = "cache/" + path.substring(libPrefix.length());
                if (!output.exists()) {
                    error("Output Does Not Exist: " + output.getAbsolutePath());
                    monitor.message("Local Path: " + output.getAbsolutePath());
                    return false;
                }

                monitor.message("Adding: " + relative);
                zout.putNextEntry(getNewEntry(relative));
                copy(output, zout);
            }

            // Add flag that forces the --offline arg to be set
            zout.putNextEntry(getNewEntry(OFFLINE_FLAG));


        } catch (IOException e) {
            e.printStackTrace();
            error("Failed to copy installer jar: " + e.getMessage());
            return false;
        }

        return true;
    }

    @Override
    protected List<Library> getLibraries() {
        List<Library> tmp = super.getLibraries();
        tmp.addAll(Arrays.asList(this.processorsClient.getLibraries()));

        // Prevent duplicates because why waste time
        List<Library> ret = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Library lib : tmp) {
            if (seen.add(lib.getName().getPath()))
                ret.add(lib);
        }

        return ret;
    }

    private boolean process(Set<File> output, PostProcessors procs, File librariesDir, File serverTarget, File target, File installer) {
        Set<File> out = procs.process(librariesDir, serverTarget, target, installer);
        if (out == null)
            return false;

        output.addAll(out);
        return true;
    }

    @Override
    public boolean isPathValid(File target) {
        return target.exists() && target.isDirectory() && target.list().length == 0;
    }

    @Override
    public String getFileError(File target) {
        if (!target.exists())
            return "The specified directory does not exist<br/>It will be created";
        else if (!target.isDirectory())
            return "The specified path needs to be a directory";
        else
            return "There are already files at the target directory";
    }

    @Override
    public String getSuccessMessage() {
        return String.format("Successfully created offline installer, downloaded %d libraries", added);
    }

    public static File findInstallerBase() {
        try {
            URI uri = Util.class.getResource("/install_profile.json").toURI();
            if (!"jar".equals(uri.getScheme()))
                throw new IllegalStateException("Could not find installer jar, if you're in a development environment, stick a pre-built installer jar in the gradle root directory and re-import your project");

            int lastExcl = uri.getRawSchemeSpecificPart().lastIndexOf("!/");
            String path = uri.getRawSchemeSpecificPart().substring(0, lastExcl);
            return Paths.get(new URI(path)).toFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to locate installer jar!", e);
        }
    }

    private File cleanTarget(File target) {
        if (target.isFile())
            return target;

        return new File(target, base.getName().substring(0, base.getName().length() - 4) + "-offline.jar");
    }

    private ZipEntry getNewEntry(String name) {
        ZipEntry ret = new ZipEntry(name);
        //ret.setTime(628041600000L);
        return ret;
    }

    private static void copy(InputStream source, OutputStream target) throws IOException {
        byte[] buf = new byte[256];
        int length;
        while ((length = source.read(buf)) != -1) {
            target.write(buf, 0, length);
        }
    }

    private static void copy(File source, OutputStream target) throws IOException {
        try (InputStream stream = new FileInputStream(source)) {
            copy(stream, target);
        }
    }
}
