package net.minecraftforge.installer.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.JOptionPane;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.IMonitor;
import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version;
import net.minecraftforge.installer.json.Version.Library;
import net.minecraftforge.installer.json.Version.LibraryDownload;

public abstract class Action {
    protected final Install profile;
    protected final IMonitor monitor;
    protected final PostProcessors processors;
    protected final Version version;
    private List<Artifact> grabbed = new ArrayList<>();

    protected Action(Install profile, boolean isClient) {
        this.profile = profile;
        this.monitor = IMonitor.buildMonitor(); //TODO: Headless argument instead of public field?
        this.processors = new PostProcessors(profile, isClient, monitor);
        this.version = Util.loadVersion(profile);
    }

    protected void error(String message) {
        if (!SimpleInstaller.headless)
            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        for (String line : message.split("\n"))
            monitor.setNote(line);
    }

    protected void info(String message) {
        for (String line : message.split("\n"))
            monitor.setNote(line);
    }

    public abstract boolean run(File target, Predicate<String> optionals);
    public abstract boolean isPathValid(File targetDir);
    public abstract String getFileError(File targetDir);
    public abstract String getSuccessMessage();

    public String getSponsorMessage() {
        return profile.getMirror() != null ? String.format(SimpleInstaller.headless ? "Data kindly mirrored by %2$s at %1$s" : "<html><a href=\'%s\'>Data kindly mirrored by %s</a></html>", profile.getMirror().getHomepage(), profile.getMirror().getName()) : null;
    }

    protected int downloadLibraries(int progress, File librariesDir, Predicate<String> optionals) {
        List<Library> libraries = new ArrayList<>();
        libraries.addAll(Arrays.asList(version.getLibraries()));
        libraries.addAll(Arrays.asList(processors.getLibraries()));

        StringBuilder output = new StringBuilder();
        for (Library lib : libraries) {
            monitor.setProgress(progress++);
            if (!DownloadUtils.downloadLibrary(monitor, profile.getMirror(), lib, librariesDir, optionals, grabbed)) {
                LibraryDownload download = lib.getDownloads() == null ? null :  lib.getDownloads().getArtifact();
                if (download != null && download.getUrl() != null) // If it doesn't have a URL we can't download it, assume we install it later
                    output.append('\n').append(lib.getName());
            }
        }
        String bad = output.toString();
        if (!bad.isEmpty()) {
            error("These libraries failed to download. Try again.\n" + bad);
            return -1;
        }
        return progress;
    }

    protected int downlaodedCount() {
        return grabbed.size();
    }

    protected int getTaskCount() {
        return profile.getLibraries().length + processors.getTaskCount();
    }
}
