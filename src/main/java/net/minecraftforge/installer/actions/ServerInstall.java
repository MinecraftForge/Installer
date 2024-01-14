/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;

public class ServerInstall extends Action {
    private List<Artifact> grabbed = new ArrayList<>();

    public ServerInstall(InstallV1 profile, ProgressCallback monitor) {
        super(profile, monitor, false);
    }

    @Override
    public boolean run(File target, File installer) throws ActionCanceledException {
        if (target.exists() && !target.isDirectory()) {
            error("There is a file at this location, the server cannot be installed here!");
            return false;
        }

        File librariesDir = new File(target,"libraries");
        if (!target.exists())
            target.mkdirs();
        librariesDir.mkdir();
        checkCancel();

        // Extract main executable jar
        Artifact contained = profile.getPath();
        if (contained != null) {
            monitor.stage("Extracting main jar:");
            if (!DownloadUtils.extractFile(contained, new File(target, contained.getFilename()), null)) {
                error("  Failed to extract main jar: " + contained.getFilename());
                return false;
            } else
                monitor.stage("  Extracted successfully");
        }
        checkCancel();

        //Download MC Server jar
        monitor.stage("Considering minecraft server jar");
        Map<String, Supplier<String>> tokens = new HashMap<>();
        tokens.put("ROOT", target::getAbsolutePath);
        tokens.put("MINECRAFT_VERSION", profile::getMinecraft);
        tokens.put("LIBRARY_DIR", librariesDir::getAbsolutePath);

        String path = Util.replaceTokens(tokens, profile.getServerJarPath());
        File serverTarget = new File(path);
        if (!downloadVanilla(serverTarget, "server"))
            return false;

        checkCancel();

        // Download Libraries
        List<File> libDirs = new ArrayList<>();
        File mcLibDir = new File(SimpleInstaller.getMCDir(), "libraries");
        if (mcLibDir.exists())
            libDirs.add(mcLibDir);

        if (!downloadLibraries(librariesDir, libDirs))
            return false;

        checkCancel();
        if (processors.process(librariesDir, serverTarget, target, installer) == null)
            return false;

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
