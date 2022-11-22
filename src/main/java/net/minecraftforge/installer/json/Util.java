/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.json;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.installer.DownloadUtils;

public class Util {
    public static Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(Artifact.class, new Artifact.Adapter())
            .create();

    public static Manifest loadManifest(InputStream stream) {
        return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Manifest.class);
    }

    public static Version getVanillaVersion(String version, File target) {
        if (!target.exists()) {
            Manifest manifest = DownloadUtils.downloadManifest();
            if (manifest == null)
                return null;
            String url = manifest.getUrl(version);
            if (url == null)
                return null;
            if (!DownloadUtils.downloadFile(target, url))
                return null;
        }
        try (InputStream stream = new FileInputStream(target)) {
            return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Version.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}