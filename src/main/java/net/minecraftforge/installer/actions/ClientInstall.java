/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.actions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.function.Predicate;
import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version;
import net.minecraftforge.installer.json.Version.Download;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ClientInstall extends Action {

    public ClientInstall(InstallV1 profile, ProgressCallback monitor) {
        super(profile, monitor, true);
    }

    @Override
    public boolean run(File target, Predicate<String> optionals, File installer) throws ActionCanceledException {
        if (!target.exists()) {
            error("There is no minecraft installation at: " + target);
            return false;
        }

        File launcherProfiles = new File(target, "launcher_profiles.json");
        File launcherProfilesMS = new File(target, "launcher_profiles_microsoft_store.json");
        if (!launcherProfiles.exists() && !launcherProfilesMS.exists()) {
            error("There is no minecraft launcher profile in \"" + target + "\", you need to run the launcher first!");
            return false;
        }

        File versionRoot = new File(target, "versions");
        File librariesDir = new File(target, "libraries");
        librariesDir.mkdir();

        checkCancel();

        // Extract version json
        monitor.stage("Extracting json");
        try (InputStream stream = Util.class.getResourceAsStream(profile.getJson())) {
            File json = new File(versionRoot, profile.getVersion() + '/' + profile.getVersion() + ".json");
            json.getParentFile().mkdirs();
            Files.copy(stream, json.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            error("  Failed to extract launcher profile json " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        checkCancel();

        // Download Vanilla main jar/json
        monitor.stage("Considering minecraft client jar");
        File versionVanilla = new File(versionRoot, profile.getMinecraft());
        if (!versionVanilla.mkdirs() && !versionVanilla.isDirectory()) {
            if (!versionVanilla.delete()) {
                error("There was a problem with the launcher version data. You will need to clear " + versionVanilla + " manually.");
                return false;
            } else
                versionVanilla.mkdirs();
        }
        checkCancel();

        File clientTarget = new File(versionVanilla, profile.getMinecraft() + ".jar");
        if (!clientTarget.exists()) {
            File versionJson = new File(versionVanilla, profile.getMinecraft() + ".json");
            Version vanilla = Util.getVanillaVersion(profile.getMinecraft(), versionJson);
            if (vanilla == null) {
                error("Failed to download version manifest, can not find client jar URL.");
                return false;
            }

            Download client = vanilla.getDownload("client");
            if (client == null) {
                error("Failed to download minecraft client, info missing from manifest: " + versionJson);
                return false;
            }

            if (!DownloadUtils.download(monitor, profile.getMirror(), client, clientTarget)) {
                clientTarget.delete();
                error("Downloading minecraft client failed, invalid checksum.\n" +
                      "Try again, or use the vanilla launcher to install the vanilla version.");
                return false;
            }
        }

        // Download Libraries
        if (!downloadLibraries(librariesDir, optionals, new ArrayList<>()))
            return false;
        checkCancel();

        /*
        String modListType = VersionInfo.getModListType();
        File modListFile = new File(target, "mods/mod_list.json");

        JsonRootNode versionJson = JsonNodeFactories.object(VersionInfo.getVersionInfo().getFields());

        if ("absolute".equals(modListType))
        {
            modListFile = new File(versionTarget, "mod_list.json");
            JsonStringNode node = (JsonStringNode)versionJson.getNode("minecraftArguments");
            try {
                Field value = JsonStringNode.class.getDeclaredField("value");
                value.setAccessible(true);
                String args = (String)value.get(node);
                value.set(node, args + " --modListFile \"absolute:"+modListFile.getAbsolutePath()+ "\"");
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        if (!"none".equals(modListType))
        {
            if (!OptionalLibrary.saveModListJson(librariesDir, modListFile, VersionInfo.getOptionals(), optionals))
            {
                JOptionPane.showMessageDialog(null, "Failed to write mod_list.json, optional mods may not be loaded.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        */

        if (!processors.process(librariesDir, clientTarget, target, installer))
            return false;

        checkCancel();

        monitor.stage("Injecting profile");
        if (launcherProfiles.exists() && !injectProfile(launcherProfiles))
            return false;
        if (launcherProfilesMS.exists() && !injectProfile(launcherProfilesMS))
            return false;

        return true;
    }

    private boolean injectProfile(File target) {
        try {
            JsonObject json = null;
            try (InputStream stream = new FileInputStream(target)) {
                json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException e) {
                error("Failed to read " + target);
                e.printStackTrace();
                return false;
            }

            JsonObject _profiles = json.getAsJsonObject("profiles");
            if (_profiles == null) {
                _profiles = new JsonObject();
                json.add("profiles", _profiles);
            }

            JsonObject _profile = _profiles.getAsJsonObject(profile.getProfile());
            if (_profile == null) {
                _profile = new JsonObject();
                _profile.addProperty("name", profile.getProfile());
                _profile.addProperty("type", "custom");
                _profiles.add(profile.getProfile(), _profile);
            }
            _profile.addProperty("lastVersionId", profile.getVersion());
            String icon = profile.getIcon();
            if (icon != null)
                _profile.addProperty("icon", icon);
            String jstring = Util.GSON.toJson(json);
            Files.write(target.toPath(), jstring.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            error("There was a problem writing the launch profile,  is it write protected?");
            return false;
        }
        return true;
    }

    @Override
    public boolean isPathValid(File targetDir) {
        return targetDir.exists() && (
            new File(targetDir, "launcher_profiles.json").exists() ||
            new File(targetDir, "launcher_profiles_microsoft_store.json").exists()
         );
    }

    @Override
    public String getFileError(File targetDir) {
        if (targetDir.exists())
            return "The directory is missing a launcher profile. Please run the minecraft launcher first";
        else
            return "There is no minecraft directory set up. Either choose an alternative, or run the minecraft launcher to create one";
    }

    @Override
    public String getSuccessMessage() {
        if (downloadedCount() > 0)
            return String.format("Successfully installed client profile %s for version %s into launcher, and downloaded %d libraries", profile.getProfile(), profile.getVersion(), downloadedCount());
        return String.format("Successfully installed client profile %s for version %s into launcher", profile.getProfile(), profile.getVersion());
    }
}
