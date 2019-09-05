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
import java.util.List;
import java.util.function.Predicate;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version;
import net.minecraftforge.installer.json.Version.Download;

public class ServerInstall extends Action {
    private List<Artifact> grabbed = new ArrayList<>();

    public ServerInstall(Install profile, ProgressCallback monitor) {
        super(profile, monitor, false);
    }

    @Override
    public boolean run(File target, Predicate<String> optionals) throws ActionCanceledException {
        if (target.exists() && !target.isDirectory()) {
            error("There is a file at this location, the server cannot be installed here!");
            return false;
        }

        File librariesDir = new File(target,"libraries");
        if (!target.exists())
            target.mkdirs();
        librariesDir.mkdir();
        if (profile.getMirror() != null)
            monitor.stage(getSponsorMessage());
        checkCancel();

        // Extract main executable jar
        Artifact contained = profile.getPath();
        monitor.stage("Extractiung main jar:");
        if (!DownloadUtils.extractFile(contained, new File(target, contained.getFilename()), null)) {
            error("  Failed to extract main jar: " + contained.getFilename());
            return false;
        } else
            monitor.stage("  Extracted successfully");
        checkCancel();

        //Download MC Server jar
        monitor.stage("Considering minecraft server jar");
        File serverTarget = new File(target,"minecraft_server." + profile.getMinecraft() + ".jar");
        if (!serverTarget.exists()) {
            File versionJson = new File(target, profile.getMinecraft() + ".json");
            Version vanilla = Util.getVanillaVersion(profile.getMinecraft(), versionJson);
            if (vanilla == null) {
                error("Failed to download version manifest, can not find server jar URL.");
                return false;
            }
            Download server = vanilla.getDownload("server");
            if (server == null) {
                error("Failed to download minecraft server, info missing from manifest: " + versionJson);
                return false;
            }

            if (!DownloadUtils.download(monitor, profile.getMirror(), server, serverTarget)) {
                serverTarget.delete();
                error("Downloading minecraft server failed, invalid checksum.\n" +
                      "Try again, or manually place server jar to skip download.");
                return false;
            }
        }
        checkCancel();

        // Download Libraries
        if (!downloadLibraries(librariesDir, optionals))
            return false;

        checkCancel();
        if (!processors.process(librariesDir, serverTarget))
            return false;

        // TODO: Optionals
        //if (!OptionalLibrary.saveModListJson(librariesDir, new File(target, "mods/mod_list.json"), VersionInfo.getOptionals(), optionals))
        //    return false;

        return true;
    }

    @Override
    public boolean isPathValid(File targetDir) {
        return targetDir.exists() && targetDir.isDirectory() && targetDir.list().length == 0;
    }

    @Override
    public String getFileError(File targetDir) {
        if (!targetDir.exists())
            return "The specified directory does not exist<br/>It will be created";
        else if (!targetDir.isDirectory())
            return "The specified path needs to be a directory";
        else
            return "There are already files at the target directory";
    }

    @Override
    public String getSuccessMessage() {
        if (grabbed.size() > 0)
            return String.format("Successfully downloaded minecraft server, downloaded %d libraries and installed %s", grabbed.size(), profile.getVersion());
        return String.format("Successfully downloaded minecraft server and installed %s", profile.getVersion());
    }
}
