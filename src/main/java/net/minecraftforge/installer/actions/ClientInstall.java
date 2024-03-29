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
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ClientInstall extends Action {

    public ClientInstall(InstallV1 profile, ProgressCallback monitor) {
        super(profile, monitor, true);
    }

    @Override
    public boolean run(File target, File installer) throws ActionCanceledException {
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
        if (!downloadVanilla(clientTarget, "client"))
            return false;

        // Download Libraries
        if (!downloadLibraries(librariesDir, new ArrayList<>()))
            return false;

        checkCancel();

        if (processors.process(librariesDir, clientTarget, target, installer) == null)
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
