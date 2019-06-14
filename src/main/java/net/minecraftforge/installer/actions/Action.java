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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.JOptionPane;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version;
import net.minecraftforge.installer.json.Version.Library;
import net.minecraftforge.installer.json.Version.LibraryDownload;

public abstract class Action {
    protected final Install profile;
    protected final ProgressCallback monitor;
    protected final PostProcessors processors;
    protected final Version version;
    private List<Artifact> grabbed = new ArrayList<>();

    protected Action(Install profile, ProgressCallback monitor, boolean isClient) {
        this.profile = profile;
        this.monitor = monitor;
        this.processors = new PostProcessors(profile, isClient, monitor);
        this.version = Util.loadVersion(profile);
    }

    protected void error(String message) {
        if (!SimpleInstaller.headless)
            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        monitor.stage(message);
    }

    public abstract boolean run(File target, Predicate<String> optionals) throws ActionCanceledException;
    public abstract boolean isPathValid(File targetDir);
    public abstract String getFileError(File targetDir);
    public abstract String getSuccessMessage();

    public String getSponsorMessage() {
        return profile.getMirror() != null ? String.format(SimpleInstaller.headless ? "Data kindly mirrored by %2$s at %1$s" : "<html><a href=\'%s\'>Data kindly mirrored by %s</a></html>", profile.getMirror().getHomepage(), profile.getMirror().getName()) : null;
    }

    protected boolean downloadLibraries(File librariesDir, Predicate<String> optionals) throws ActionCanceledException {
        List<Library> libraries = new ArrayList<>();
        libraries.addAll(Arrays.asList(version.getLibraries()));
        libraries.addAll(Arrays.asList(processors.getLibraries()));

        StringBuilder output = new StringBuilder();
        final double steps = libraries.size();
        int progress = 1;
        monitor.start("Downloading libraries");
        for (Library lib : libraries) {
            checkCancel();
            monitor.progress(progress++ / steps);
            if (!DownloadUtils.downloadLibrary(monitor, profile.getMirror(), lib, librariesDir, optionals, grabbed)) {
                LibraryDownload download = lib.getDownloads() == null ? null :  lib.getDownloads().getArtifact();
                if (download != null && !download.getUrl().isEmpty()) // If it doesn't have a URL we can't download it, assume we install it later
                    output.append('\n').append(lib.getName());
            }
        }
        String bad = output.toString();
        if (!bad.isEmpty()) {
            error("These libraries failed to download. Try again.\n" + bad);
            return false;
        }
        return true;
    }

    protected int downlaodedCount() {
        return grabbed.size();
    }

    protected int getTaskCount() {
        return profile.getLibraries().length + processors.getTaskCount();
    }

    protected void checkCancel() throws ActionCanceledException {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new ActionCanceledException(e);
        }
    }
}
