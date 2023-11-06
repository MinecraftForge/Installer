/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Manifest;
import net.minecraftforge.installer.json.Mirror;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version.Download;
import net.minecraftforge.installer.json.Version.Library;
import net.minecraftforge.installer.json.Version.LibraryDownload;

public class DownloadUtils {
    public static final String LIBRARIES_URL = "https://libraries.minecraft.net/";
    public static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    public static boolean OFFLINE_MODE = false;

    public static boolean downloadLibrary(ProgressCallback monitor, Mirror mirror, Library library, File root, Predicate<String> optional, List<Artifact> grabbed, List<File> additionalLibraryDirs) {
        Artifact artifact = library.getName();
        File target = artifact.getLocalPath(root);
        LibraryDownload download = library.getDownloads() == null ? null :  library.getDownloads().getArtifact();
        if (download == null) {
            download = new LibraryDownload();
            download.setPath(artifact.getPath());
        }

        if (!optional.test(library.getName().getDescriptor())) {
            monitor.message(String.format("Considering library %s: Not Downloading {Disabled}", artifact.getDescriptor()));
            return true;
        }

        monitor.message(String.format("Considering library %s", artifact.getDescriptor()));

        if (target.exists()) {
            if (download.getSha1() != null) {
                String sha1 = getSha1(target);
                if (download.getSha1().equals(sha1)) {
                    monitor.message("  File exists: Checksum validated.");
                    return true;
                }
                monitor.message("  File exists: Checksum invalid, deleting file:");
                monitor.message("    Expected: " + download.getSha1());
                monitor.message("    Actual:   " + sha1);
                if (!target.delete()) {
                    monitor.stage("    Failed to delete file, aborting.");
                    return false;
                }
            } else {
                monitor.message("  File exists: No checksum, Assuming valid.");
                return true;
            }
        }

        target.getParentFile().mkdirs();

        // Try extracting first
        try (final InputStream input = DownloadUtils.class.getResourceAsStream("/maven/" + artifact.getPath())) {
            if (input != null) {
                monitor.message("  Extracting library from /maven/" + artifact.getPath());
                Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (download.getSha1() != null) {
                    String sha1 = getSha1(target);
                    if (download.getSha1().equals(sha1)) {
                        monitor.message("    Extraction completed: Checksum validated.");
                        grabbed.add(artifact);
                        return true;
                    }
                    monitor.message("    Extraction failed: Checksum invalid, deleting file:");
                    monitor.message("      Expected: " + download.getSha1());
                    monitor.message("      Actual:   " + sha1);
                    if (!target.delete()) {
                        monitor.stage("      Failed to delete file, aborting.");
                        return false;
                    }
                    return false;
                } else {
                    monitor.message("    Extraction completed: No checksum, Assuming valid.");
                }
                grabbed.add(artifact);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        // Try searching local installs if the file can be validated
        if (download.getSha1() != null) {
            String providedSha1 = download.getSha1();
            for (File libDir : additionalLibraryDirs) {
                File inLibDir = new File(libDir, artifact.getPath());
                if (inLibDir.exists()) {
                    monitor.message(String.format("  Found artifact in local folder %s", libDir.toString()));
                    String sha1 = DownloadUtils.getSha1(inLibDir);
                    if (providedSha1.equals(sha1)) {
                        monitor.message("    Checksum validated");
                    } else {
                        // Do not fail immediately. We may have other sources
                        monitor.message("    Invalid checksum. Not using.");
                        continue;
                    }
                    // Valid checksum, copy the lib
                    try {
                        Files.copy(inLibDir.toPath(), target.toPath());
                        monitor.message("    Successfully copied local file");
                        grabbed.add(artifact);
                        return true;
                    } catch (IOException e) {
                        // The copy may have failed when the file is in use. Don't abort, we may have other sources
                        e.printStackTrace();
                        monitor.message(String.format("    Failed to copy from local folder: %s", e.toString()));
                        // Clean up the file that may have been created if the copy failed
                        if (target.exists()) {
                            if (!target.delete()) {
                                monitor.message("    Failed to delete failed copy, aborting");
                                return false;
                            }
                        }
                    }
                }
            }
        }

        String url = download.getUrl();
        if (url == null || url.isEmpty()) {
            monitor.message("  Invalid library, missing url");
            return false;
        }

        if (download(monitor, mirror, download, target)) {
            grabbed.add(artifact);
            return true;
        }
        return false;
    }

    public static boolean download(ProgressCallback monitor, Mirror mirror, LibraryDownload download, File target) {
        String url = download.getUrl();
        if (url.startsWith("http") && !url.startsWith(LIBRARIES_URL) && mirror != null && url.endsWith(download.getPath())) {
            // TODO: Vanilla launcher is dumb so we fake classifier only deps. One day the launcher will be sane/document...
            // Anyways, the path is not the same as the real maven path. So we don't have a good way to determine the mirrored url
            if (download(monitor, mirror, download, target, mirror.getUrl() + download.getPath())) // Use unmirrored if mirror fails.
                return true;
        }
        return download(monitor, mirror, download, target, url);
    }

    public static boolean download(ProgressCallback monitor, Mirror mirror, Download download, File target) {
        return download(monitor, mirror, download, target, download.getUrl());
    }

    private static boolean download(ProgressCallback monitor, Mirror mirror, Download download, File target, String url) {
        monitor.message("  Downloading library from " + url);
        try {
            URLConnection connection = getConnection(url);
            if (connection != null) {
                Files.copy(connection.getInputStream(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

                if (download.getSha1() != null) {
                    String sha1 = getSha1(target);
                    if (download.getSha1().equals(sha1)) {
                        monitor.message("    Download completed: Checksum validated.");
                        return true;
                    }
                    monitor.message("    Download failed: Checksum invalid, deleting file:");
                    monitor.message("      Expected: " + download.getSha1());
                    monitor.message("      Actual:   " + sha1);
                    if (!target.delete()) {
                        monitor.stage("      Failed to delete file, aborting.");
                        return false;
                    }
                }
                monitor.message("    Download completed: No checksum, Assuming valid.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getSha1(File target) {
        try {
            return HashFunction.SHA1.hash(Files.readAllBytes(target.toPath())).toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean checksumValid(File target, String checksum) {
        if (checksum == null || checksum.isEmpty())
            return true;
        String sha1 = getSha1(target);
        return sha1 != null && sha1.equals(checksum);
    }

    private static URLConnection getConnection(String address) {
        if (OFFLINE_MODE) {
            System.out.println("Offline Mode: Not downloading: " + address);
            return null;
        }

        URL url = null;
        try {
            url = new URL(address);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }

        try {
            int MAX = 3;
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
        } catch (SSLHandshakeException e) {
            System.out.println("Failed to establish connection to " + address);
            String host = url.getHost();
            System.out.println(" Host: " + host + " [" + getIps(host).stream().collect(Collectors.joining(", ")) + "]");
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<String> getIps(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            return Arrays.stream(addresses).map(InetAddress::getHostAddress).collect(Collectors.toList());
        } catch (UnknownHostException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    public static boolean checkCertificate(String host) {
        try {
            HttpURLConnection con = (HttpURLConnection)new URL(host).openConnection();
            con.setRequestMethod("HEAD");
            con.connect();
            return true;
        } catch (SSLException e) {
            System.out.println("Error checking " + host + ": " + e.getMessage());
            return false; // Something screwed up with the SSL connection, most likely missing root certs.
        } catch (IOException e) {
            return true; // Something else went wrong, assume the server is down or something, and let it error later down the line.
        }
    }

    public static boolean downloadFileEtag(File target, String url) {
        try {
            URLConnection connection = getConnection(url);
            String etag = connection.getHeaderField("ETag");
            if (etag == null)
              etag = "-";
            else if ((etag.startsWith("\"")) && (etag.endsWith("\"")))
                etag = etag.substring(1, etag.length() - 1);

            Files.copy(connection.getInputStream(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

            if (etag.indexOf('-') != -1) return true; //No-etag, assume valid
            byte[] fileData = Files.readAllBytes(target.toPath());
            String md5 = HashFunction.MD5.hash(fileData).toString();
            System.out.println("  ETag: " + etag);
            System.out.println("  MD5:  " + md5);
            return etag.equalsIgnoreCase(md5);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Mirror[] downloadMirrors(String url) {
        try {
            URLConnection connection = getConnection(url);
            if (connection != null) {
                try (InputStream stream = connection.getInputStream()) {
                    return Util.loadMirrorList(stream);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
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

    public static boolean extractFile(Artifact art, File target, String checksum) {
        final InputStream input = DownloadUtils.class.getResourceAsStream("/maven/" + art.getPath());
        if (input == null) {
            System.out.println("File not found in installer archive: /maven/" + art.getPath());
            return false;
        }

        if (!target.getParentFile().exists())
            target.getParentFile().mkdirs();

        try {
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return checksumValid(target, checksum);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean extractFile(String name, File target) {
        final String path = name.charAt(0) == '/' ? name : '/' + name;
        final InputStream input = DownloadUtils.class.getResourceAsStream(path);
        if (input == null) {
            System.out.println("File not found in installer archive: " + path);
            return false;
        }

        if (!target.getParentFile().exists())
            target.getParentFile().mkdirs();

        try {
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true; //checksumValid(target, checksum); //TODO: zip checksums?
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
