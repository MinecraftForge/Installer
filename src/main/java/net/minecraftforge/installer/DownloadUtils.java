/*
 * Installer
 * Copyright (c) 2016-2021.
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
package net.minecraftforge.installer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import javax.swing.ProgressMonitor;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

public class DownloadUtils {
    public static final String LIBRARIES_URL = "https://libraries.minecraft.net/";
    //TODO: Pull from manifests
    public static final String VERSION_URL_SERVER = "https://s3.amazonaws.com/Minecraft.Download/versions/{MCVER}/minecraft_server.{MCVER}.jar";
    public static final String VERSION_URL_CLIENT = "https://s3.amazonaws.com/Minecraft.Download/versions/{MCVER}/{MCVER}.jar";
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
        try
        {
            if (checksums == null || checksums.isEmpty())
                return true;
            byte[] fileData = Files.toByteArray(libPath);
            boolean valid = checksums.contains(Hashing.sha1().hashBytes(fileData).toString());
            if (!valid && libPath.getName().endsWith(".jar"))
            {
                valid = validateJar(libPath, fileData, checksums);
            }
            return valid;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean downloadFileEtag(String libName, File libPath, String libURL)
    {
        if (OFFLINE_MODE)
        {
            System.out.println("Offline Mode: Not downloading: " + libURL);
            return false;
        }

        try
        {
            URL url = new URL(libURL);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            String etag = connection.getHeaderField("ETag");
            if (etag == null)
            {
              etag = "-";
            }
            else if ((etag.startsWith("\"")) && (etag.endsWith("\"")))
            {
                etag = etag.substring(1, etag.length() - 1);
            }

            InputSupplier<InputStream> urlSupplier = new URLISSupplier(connection);
            Files.copy(urlSupplier, libPath);

            if (etag.indexOf('-') != -1) return true; //No-etag, assume valid
            try
            {
                byte[] fileData = Files.toByteArray(libPath);
                String md5 = Hashing.md5().hashBytes(fileData).toString();
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
        catch (FileNotFoundException fnf)
        {
            fnf.printStackTrace();
            return false;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean validateJar(File libPath, byte[] data, List<String> checksums) throws IOException
    {
        System.out.println("Checking \"" + libPath.getAbsolutePath() + "\" internal checksums");

        HashMap<String, String> files = new HashMap<String, String>();
        String[] hashes = null;
        JarInputStream jar = new JarInputStream(new ByteArrayInputStream(data));
        JarEntry entry = jar.getNextJarEntry();
        while (entry != null)
        {
            byte[] eData = readFully(jar);

            if (entry.getName().equals("checksums.sha1"))
            {
                hashes = new String(eData, Charset.forName("UTF-8")).split("\n");
            }

            if (!entry.isDirectory())
            {
                files.put(entry.getName(), Hashing.sha1().hashBytes(eData).toString());
            }
            entry = jar.getNextJarEntry();
        }
        jar.close();

        if (hashes != null)
        {
            boolean failed = !checksums.contains(files.get("checksums.sha1"));
            if (failed)
            {
                System.out.println("    checksums.sha1 failed validation");
            }
            else
            {
                System.out.println("    checksums.sha1 validated successfully");
                for (String hash : hashes)
                {
                    if (hash.trim().equals("") || !hash.contains(" ")) continue;
                    String[] e = hash.split(" ");
                    String validChecksum = e[0];
                    String target = hash.substring(validChecksum.length() + 1);
                    String checksum = files.get(target);

                    if (!files.containsKey(target) || checksum == null)
                    {
                        System.out.println("    " + target + " : missing");
                        failed = true;
                    }
                    else if (!checksum.equals(validChecksum))
                    {
                        System.out.println("    " + target + " : failed (" + checksum + ", " + validChecksum + ")");
                        failed = true;
                    }
                }
            }

            if (!failed)
            {
                System.out.println("    Jar contents validated successfully");
            }

            return !failed;
        }
        else
        {
            System.out.println("    checksums.sha1 was not found, validation failed");
            return false; //Missing checksums
        }
    }

    public static List<String> downloadList(String libURL)
    {
        if (OFFLINE_MODE)
        {
            System.out.println("Offline Mode: Not downloading: " + libURL);
            return Lists.newArrayList();
        }

        try
        {
            URL url = new URL(libURL);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            InputSupplier<InputStream> urlSupplier = new URLISSupplier(connection);
            return CharStreams.readLines(CharStreams.newReaderSupplier(urlSupplier, Charsets.UTF_8));
        }
        catch (Exception e)
        {
            return Collections.emptyList();
        }

    }

    public static boolean downloadFile(String libName, File libPath, String libURL, List<String> checksums)
    {
        if (OFFLINE_MODE)
        {
            System.out.println("Offline Mode: Not downloading: " + libURL);
            return false;
        }

        try
        {
            URL url = new URL(libURL);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            InputSupplier<InputStream> urlSupplier = new URLISSupplier(connection);
            Files.copy(urlSupplier, libPath);
            if (checksumValid(libPath, checksums))
            {
                return true;
            }
            else
            {
                return false;
            }
        }
        catch (FileNotFoundException fnf)
        {
            fnf.printStackTrace();
            return false;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
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
            Files.copy(new InputSupplier<InputStream>()
            {
                @Override
                public InputStream getInput() throws IOException
                {
                    return input;
                }
            }, libPath);
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

    static class URLISSupplier implements InputSupplier<InputStream>
    {
        private final URLConnection connection;

        private URLISSupplier(URLConnection connection)
        {
            this.connection = connection;
        }

        @Override
        public InputStream getInput() throws IOException
        {
            return connection.getInputStream();
        }
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
}
