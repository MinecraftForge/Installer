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
import net.minecraftforge.installer.json.Version.Library;

public class ServerInstall extends Action {
    private List<Artifact> grabbed = new ArrayList<>();

    public ServerInstall(Install profile) {
        super(profile);
    }

    @Override
    public boolean run(File target, Predicate<String> optionals) {
        if (target.exists() && !target.isDirectory()) {
            error("There is a file at this location, the server cannot be installed here!");
            return false;
        }

        File librariesDir = new File(target,"libraries");
        if (!target.exists())
            target.mkdirs();
        librariesDir.mkdir();
        if (profile.getMirror() != null)
            monitor.setNote(getSponsorMessage());

        Version version = Util.loadVersion(profile);

        Library[] libraries = version.getLibraries();
        monitor.setSteps(libraries.length + 2); //Extract executable + download server jar
        int progress = 1;

        // Extract main executable jar
        Artifact contained = profile.getPath();
        monitor.setProgress(progress++);
        info("Extractiung main jar:");
        if (!DownloadUtils.extractFile(contained, new File(target, contained.getFilename()), null)) {
            error("  Failed to extract main jar: " + contained.getFilename());
            return false;
        } else
            info("  Extracted successfully");

        //Download MC Server jar
        info("Considering minecraft server jar");
        monitor.setProgress(progress++);
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

        // Download Libraries
        StringBuilder output = new StringBuilder();
        for (int x = 0; x < libraries.length; x++) {
            Library lib = libraries[x];
            monitor.setProgress(progress++);
            if (contained.getDescriptor().equals(lib.getName())) //Executable, skip it as we extracted it above.
                continue;
            if (!DownloadUtils.downloadLibrary(monitor, profile.getMirror(), lib, librariesDir, optionals, grabbed)) {
                output.append('\n').append(lib.getArtifact());
            }
        }
        String bad = output.toString();
        if (!bad.isEmpty()) {
            error("These libraries failed to download. Try again.\n" + bad);
            return false;
        }

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
