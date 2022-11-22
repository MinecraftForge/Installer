/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.ProgressMonitor;

import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Manifest;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version.Download;

public class DownloadUtils {
    public static final String LIBRARIES_URL = "https://libraries.minecraft.net/";
    public static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    public static boolean OFFLINE_MODE = false;

    public static int downloadInstalledLibraries(boolean isClient, File librariesDir, IMonitor monitor, List<LibraryInfo> libraries, int progress, List<Artifact> grabbed, List<Artifact> bad)
    {
        for (LibraryInfo library : libraries)
        {
            Artifact artifact = library.getArtifact();
            List<String> checksums = library.getChecksums();
            if (library.isCorrectSide() && library.isEnabled())
            {
                monitor.setNote(String.format("Considering library %s", artifact.getDescriptor()));
                File libPath = artifact.getLocalPath(librariesDir);
                String libURL = library.getURL();
                if (libPath.exists() && checksumValid(libPath, checksums))
                {
                    monitor.setProgress(progress++);
                    continue;
                }

                libPath.getParentFile().mkdirs();
                monitor.setNote(String.format("Downloading library %s", artifact.getDescriptor()));
                libURL += artifact.getPath();

                monitor.setNote(String.format("Trying unpacked library %s", artifact.getDescriptor()));
                if (!downloadFile(artifact.getDescriptor(), libPath, libURL, checksums) &&
                    !extractFile(artifact, libPath, checksums))
                {
                    if (!libURL.startsWith(LIBRARIES_URL) || !isClient)
                    {
                        bad.add(artifact);
                    }
                    else
                    {
                        monitor.setNote("Unmrriored file failed, Mojang launcher should download at next run, non fatal");
                    }
                }
                else
                {
                    grabbed.add(artifact);
                }
            }
            else
            {
                if (library.isCorrectSide())
                    monitor.setNote(String.format("Considering library %s: Not Downloading {Disabled}", artifact.getDescriptor()));
                else
                    monitor.setNote(String.format("Considering library %s: Not Downloading {Wrong Side}", artifact.getDescriptor()));
            }
            monitor.setProgress(progress++);
        }
        return progress;
    }

    private static boolean checksumValid(File libPath, List<String> checksums)
    {
        if (checksums == null || checksums.isEmpty())
            return true;
        return checksums.contains(getSha1(libPath));
    }

    public static boolean downloadFileEtag(String libName, File libPath, String libURL)
    {
        try
        {
            URLConnection connection = getConnection(libURL);
            if (connection == null)
                return false;
            String etag = connection.getHeaderField("ETag");
            if (etag == null)
                etag = "-";
            else if ((etag.startsWith("\"")) && (etag.endsWith("\"")))
                etag = etag.substring(1, etag.length() - 1);

            if (!libPath.getParentFile().exists())
                libPath.getParentFile().mkdirs();
            Files.copy(connection.getInputStream(), libPath.toPath(), StandardCopyOption.REPLACE_EXISTING);

            if (etag.indexOf('-') != -1) return true; //No-etag, assume valid
            try
            {
                byte[] fileData = Files.readAllBytes(libPath.toPath());
                String md5 = HashFunction.MD5.hash(fileData).toString();
                System.out.println("  ETag: " + etag);
                System.out.println("  MD5:  " + md5);
                return etag.equalsIgnoreCase(md5);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                return false;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public static List<String> downloadList(String libURL)
    {
        try
        {
            URLConnection connection = getConnection(libURL);
            if (connection == null)
                return new ArrayList<>();
            byte[] data = readFully(connection.getInputStream());
            String[] lines = new String(data, StandardCharsets.UTF_8).split("\\r?\\n|\\r");
            return Arrays.asList(lines);
        }
        catch (Exception e)
        {
            return Collections.emptyList();
        }

    }

    public static boolean downloadFile(String libName, File libPath, String libURL, List<String> checksums)
    {
        try
        {
            URLConnection connection = getConnection(libURL);
            if (connection == null)
                return false;

            Files.copy(connection.getInputStream(), libPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return checksumValid(libPath, checksums);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean downloadFile(File target, String url) {
        try {
            URLConnection connection = getConnection(url);
            if (connection != null) {
                Files.copy(connection.getInputStream(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean extractFile(Artifact art, File libPath, List<String> checksums)
    {
        final InputStream input = DownloadUtils.class.getResourceAsStream("/maven/" + art.getPath());
        if (input == null)
        {
            System.out.println("File not found in installer archive: /maven/" + art.getPath());
            return false;
        }

        try
        {
            Files.copy(input, libPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return checksumValid(libPath, checksums);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public static byte[] readFully(InputStream stream) throws IOException
    {
        byte[] data = new byte[4096];
        ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
        int len;
        do
        {
            len = stream.read(data);
            if (len > 0)
            {
                entryBuffer.write(data, 0, len);
            }
        } while (len != -1);

        return entryBuffer.toByteArray();
    }

    public static IMonitor buildMonitor()
    {
        if (ServerInstall.headless)
        {
            return new IMonitor()
            {

                @Override
                public void setMaximum(int max)
                {
                }

                @Override
                public void setNote(String note)
                {
                    System.out.println("MESSAGE: "+ note);
                }

                @Override
                public void setProgress(int progress)
                {

                }

                @Override
                public void close()
                {

                }

            };
        }
        else
        {
            return new IMonitor() {
                private ProgressMonitor monitor;
                {
                    monitor = new ProgressMonitor(null, "Downloading libraries", "Libraries are being analyzed", 0, 1);
                    monitor.setMillisToPopup(0);
                    monitor.setMillisToDecideToPopup(0);
                }
                @Override
                public void setMaximum(int max)
                {
                    monitor.setMaximum(max);
                }

                @Override
                public void setNote(String note)
                {
                    System.out.println(note);
                    monitor.setNote(note);
                }

                @Override
                public void setProgress(int progress)
                {
                    monitor.setProgress(progress);
                }

                @Override
                public void close()
                {
                    monitor.close();
                }
            };
        }
    }

    public static String getSha1(File target) {
        try {
            return HashFunction.SHA1.hash(Files.readAllBytes(target.toPath())).toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static URLConnection getConnection(String address) {
        if (OFFLINE_MODE) {
            System.out.println("Offline Mode: Not downloading: " + address);
            return null;
        }

        try {
            int MAX = 3;
            URL url = new URL(address);
            URLConnection connection = null;
            for (int x = 0; x < MAX; x++) { //Maximum of 3 redirects.
                connection = url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                if (connection instanceof HttpURLConnection) {
                    HttpURLConnection hcon = (HttpURLConnection)connection;
                    hcon.setInstanceFollowRedirects(false);
                    int res = hcon.getResponseCode();
                    if (res == HttpURLConnection.HTTP_MOVED_PERM || res == HttpURLConnection.HTTP_MOVED_TEMP) {
                        String location = hcon.getHeaderField("Location");
                        hcon.disconnect(); //Kill old connection.
                        if (x == MAX-1) {
                            System.out.println("Invalid number of redirects: " + location);
                            return null;
                        } else {
                            System.out.println("Following redirect: " + location);
                            url = new URL(url, location); // Nested in case of relative urls.
                        }
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            return connection;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Manifest downloadManifest() {
        try {
            URLConnection connection = getConnection(MANIFEST_URL);
            if (connection != null) {
                try (InputStream stream = connection.getInputStream()) {
                    return Util.loadManifest(stream);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean download(IMonitor monitor, Download download, File target) {
        return download(monitor, download, target, download.getUrl());
    }

    private static boolean download(IMonitor monitor, Download download, File target, String url) {
        monitor.setNote("  Downloading library from " + url);
        try {
            URLConnection connection = getConnection(url);
            if (connection != null) {
                Files.copy(connection.getInputStream(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

                if (download.getSha1() != null) {
                    String sha1 = getSha1(target);
                    if (download.getSha1().equals(sha1)) {
                        monitor.setNote("    Download completed: Checksum validated.");
                        return true;
                    }
                    monitor.setNote("    Download failed: Checksum invalid, deleting file:");
                    monitor.setNote("      Expected: " + download.getSha1());
                    monitor.setNote("      Actual:   " + sha1);
                    if (!target.delete()) {
                        monitor.setNote("      Failed to delete file, aborting.");
                        return false;
                    }
                }
                monitor.setNote("    Download completed: No checksum, Assuming valid.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
