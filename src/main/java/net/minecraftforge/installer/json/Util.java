/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.installer.DownloadUtils;

public class Util {
    public static Gson GSON = new GsonBuilder().setPrettyPrinting()
            .registerTypeAdapter(Artifact.class, new Artifact.Adapter())
            .create();

    public static InstallV1 loadInstallProfile() {
        byte[] data = null;
        try (InputStream stream = Util.class.getResourceAsStream("/install_profile.json")) {
            data = readFully(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Spec spec = GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8), Spec.class);
        switch (spec.getSpec()) {
            case 0: return new InstallV1(GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8), Install.class));
            case 1: return GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8), InstallV1.class);
            default: throw new IllegalArgumentException("Invalid launcher profile spec: " + spec.getSpec() + " Only 0, and 1 are supported");
        }
    }

    public static Mirror[] loadMirrorList(InputStream stream) {
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

    private static byte[] readFully(InputStream stream) throws IOException {
        byte[] data = new byte[4096];
        ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
        int len;
        do {
            len = stream.read(data);
            if (len > 0)
                entryBuffer.write(data, 0, len);
        } while (len != -1);

        return entryBuffer.toByteArray();
    }

    public static String replaceTokens(Map<String, String> tokens, String value) {
        StringBuilder buf = new StringBuilder();

        for (int x = 0; x < value.length(); x++) {
            char c = value.charAt(x);
            if (c == '\\') {
                if (x == value.length() - 1)
                    throw new IllegalArgumentException("Illegal pattern (Bad escape): " + value);
                buf.append(value.charAt(++x));
            } else if (c == '{' || c ==  '\'') {
                StringBuilder key = new StringBuilder();
                for (int y = x + 1; y <= value.length(); y++) {
                    if (y == value.length())
                        throw new IllegalArgumentException("Illegal pattern (Unclosed " + c + "): " + value);
                    char d = value.charAt(y);
                    if (d == '\\') {
                        if (y == value.length() - 1)
                            throw new IllegalArgumentException("Illegal pattern (Bad escape): " + value);
                        key.append(value.charAt(++y));
                    } else if (c == '{' && d == '}') {
                        x = y;
                        break;
                    } else if (c == '\'' && d == '\'') {
                        x = y;
                        break;
                    } else
                        key.append(d);
                }
                if (c == '\'')
                    buf.append(key);
                else {
                    if (!tokens.containsKey(key.toString()))
                        throw new IllegalArgumentException("Illegal pattern: " + value + " Missing Key: " + key);
                    buf.append(tokens.get(key.toString()));
                }
            } else {
                buf.append(c);
            }
        }

        return buf.toString();
    }
}
