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

    public static Install loadInstallProfile() {
        try (InputStream stream = Util.class.getResourceAsStream("/install_profile.json")) {
            return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Install.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Mirror[] loadMirriorList(InputStream stream) {
        return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Mirror[].class);
    }

    public static Manifest loadManifest(InputStream stream) {
        return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Manifest.class);
    }

    public static Version loadVersion(Install profile) {
        try (InputStream stream = Util.class.getResourceAsStream(profile.getJson())) {
            return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Version.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
