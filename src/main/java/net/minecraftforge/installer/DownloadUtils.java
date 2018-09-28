package net.minecraftforge.installer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.function.Predicate;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;
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

    public static boolean downloadLibrary(IMonitor monitor, Mirror mirror, Library library, File root, Predicate<String> optional, List<Artifact> grabbed) {
        Artifact artifact = library.getName();
        File target = artifact.getLocalPath(root);
        LibraryDownload download = library.getDownloads() == null ? null :  library.getDownloads().getArtifact();
        if (download == null) {
            download = new LibraryDownload();
            download.setPath(artifact.getPath());
        }

        if (!optional.test(library.getName().getDescriptor())) {
            monitor.setNote(String.format("Considering library %s: Not Downloading {Disabled}", artifact.getDescriptor()));
            return true;
        }

        monitor.setNote(String.format("Considering library %s", artifact.getDescriptor()));

        if (target.exists()) {
            if (download.getSha1() != null) {
                String sha1 = getSha1(target);
                if (download.getSha1().equals(sha1)) {
                    monitor.setNote("  File exists: Checksum validated.");
                    return true;
                }
                monitor.setNote("  File exists: Checksum invalid, deleting file:");
                monitor.setNote("    Expected: " + download.getSha1());
                monitor.setNote("    Actual:   " + sha1);
                if (!target.delete()) {
                    monitor.setNote("    Failed to delete file, aborting.");
                    return false;
                }
            } else {
                monitor.setNote("  File exists: No checksum, Assuming valid.");
                return true;
            }
        }

        target.getParentFile().mkdirs();

        // Try extracting first
        try (final InputStream input = DownloadUtils.class.getResourceAsStream("/maven/" + artifact.getPath())) {
            if (input != null) {
                monitor.setNote("  Extracting library from /maven/" + artifact.getPath());
                Files.copy(() -> input, target);
                if (download.getSha1() != null) {
                    String sha1 = getSha1(target);
                    if (download.getSha1().equals(sha1)) {
                        monitor.setNote("    Extraction completed: Checksum validated.");
                        grabbed.add(artifact);
                        return true;
                    }
                    monitor.setNote("    Extraction failed: Checksum invalid, deleting file:");
                    monitor.setNote("      Expected: " + download.getSha1());
                    monitor.setNote("      Actual:   " + sha1);
                    if (!target.delete()) {
                        monitor.setNote("      Failed to delete file, aborting.");
                        return false;
                    }
                }
                monitor.setNote("    Extraction completed: No checksum, Assuming valid.");
                grabbed.add(artifact);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        String url = download.getUrl();
        if (url == null) {
            monitor.setNote("  Invalid library, missing url");
            return false;
        }

        if (download(monitor, mirror, download, target)) {
            grabbed.add(artifact);
            return true;
        }
        return false;
    }

    public static boolean download(IMonitor monitor, Mirror mirror, LibraryDownload download, File target) {
        String url = download.getUrl();
        if (!url.startsWith(LIBRARIES_URL) && mirror != null)
            url = mirror.getUrl() + download.getPath();
        return download(monitor, mirror, download, target, url);
    }

    public static boolean download(IMonitor monitor, Mirror mirror, Download download, File target) {
        return download(monitor, mirror, download, target, download.getUrl());
    }

    private static boolean download(IMonitor monitor, Mirror mirror, Download download, File target, String url) {
        monitor.setNote("  Downloading library from " + url);
        try {
            URLConnection connection = getConnection(url);
            if (connection != null) {
                Files.copy(() -> connection.getInputStream(), target);

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

    public static String getSha1(File target) {
        try {
            return Hashing.sha1().hashBytes(Files.toByteArray(target)).toString();
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

    private static URLConnection getConnection(String url) {
        if (OFFLINE_MODE) {
            System.out.println("Offline Mode: Not downloading: " + url);
            return null;
        }

        try {
            URLConnection connection = new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            return connection;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
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

            Files.copy(() -> connection.getInputStream(), target);

            if (etag.indexOf('-') != -1) return true; //No-etag, assume valid
            byte[] fileData = Files.toByteArray(target);
            String md5 = Hashing.md5().hashBytes(fileData).toString();
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
                    return Util.loadMirriorList(stream);
                }
            }
        } catch (IOException e) {
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
                Files.copy(() -> connection.getInputStream(), target);
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
            Files.copy(() -> input, target);
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
            Files.copy(() -> input, target);
            return true; //checksumValid(target, checksum); //TODO: zip checksums?
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static byte[] readFully(InputStream stream) throws IOException {
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
}
