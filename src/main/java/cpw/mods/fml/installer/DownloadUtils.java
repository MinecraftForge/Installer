package cpw.mods.fml.installer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;

import javax.swing.ProgressMonitor;

import org.tukaani.xz.XZInputStream;

import argo.jdom.JsonNode;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

public class DownloadUtils {
    public static final String LIBRARIES_URL = "https://libraries.minecraft.net/";
    public static final String VERSION_URL_SERVER = "https://s3.amazonaws.com/Minecraft.Download/versions/{MCVER}/minecraft_server.{MCVER}.jar";

    private static final String PACK_NAME = ".pack.xz";
    public static int downloadInstalledLibraries(String jsonMarker, File librariesDir, IMonitor monitor, List<JsonNode> libraries, int progress, List<String> grabbed, List<String> bad)
    {
        for (JsonNode library : libraries)
        {
            String libName = library.getStringValue("name");
            List<String> checksums = null;
            if (library.isArrayNode("checksums"))
            {
                checksums = Lists.newArrayList(Lists.transform(library.getArrayNode("checksums"), new Function<JsonNode, String>() {
                    public String apply(JsonNode node)
                    {
                        return node.getText();
                    }
                }));
            }
            monitor.setNote(String.format("Considering library %s", libName));
            if (library.isBooleanValue(jsonMarker) && library.getBooleanValue(jsonMarker))
            {
                String[] nameparts = Iterables.toArray(Splitter.on(':').split(libName), String.class);
                nameparts[0] = nameparts[0].replace('.', '/');
                String jarName = nameparts[1] + '-' + nameparts[2] + ".jar";
                String pathName = nameparts[0] + '/' + nameparts[1] + '/' + nameparts[2] + '/' + jarName;
                File libPath = new File(librariesDir, pathName.replace('/', File.separatorChar));
                String libURL = LIBRARIES_URL;
                if (MirrorData.INSTANCE.hasMirrors() && library.isStringValue("url"))
                {
                    libURL = MirrorData.INSTANCE.getMirrorURL();
                }
                else if (library.isStringValue("url"))
                {
                    libURL = library.getStringValue("url") + "/";
                }
                if (libPath.exists() && checksumValid(libPath, checksums))
                {
                    monitor.setProgress(progress++);
                    continue;
                }

                libPath.getParentFile().mkdirs();
                monitor.setNote(String.format("Downloading library %s", libName));
                libURL += pathName;
                File packFile = new File(libPath.getParentFile(), libPath.getName() + PACK_NAME);
                if (!downloadFile(libName, packFile, libURL + PACK_NAME, null))
                {
                    if (library.isStringValue("url"))
                    {
                        monitor.setNote(String.format("Trying unpacked library %s", libName));
                    }
                    if (!downloadFile(libName, libPath, libURL, checksums))
                    {
                        if (!libURL.startsWith(LIBRARIES_URL) || !jsonMarker.equals("clientreq"))
                        {
                            bad.add(libName);
                        }
                        else
                        {
                            monitor.setNote("Unmrriored file failed, Mojang launcher should download at next run, non fatal");
                        }
                    }
                    else
                    {
                        grabbed.add(libName);
                    }
                }
                else
                {
                    try
                    {
                        monitor.setNote(String.format("Unpacking packed file %s",packFile.getName()));
                        unpackLibrary(libPath, Files.toByteArray(packFile));
                        monitor.setNote(String.format("Successfully unpacked packed file %s",packFile.getName()));
                        packFile.delete();

                        if (checksumValid(libPath, checksums))
                        {
                            grabbed.add(libName);
                        }
                        else
                        {
                            bad.add(libName);
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                        bad.add(libName);
                    }
                }
            }
            monitor.setProgress(progress++);
        }
        return progress;
    }

    private static boolean checksumValid(File libPath, List<String> checksums)
    {
        try
        {
            byte[] fileData = Files.toByteArray(libPath);
            boolean valid = checksums == null || checksums.isEmpty() || checksums.contains(Hashing.sha1().hashBytes(fileData).toString());
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

    public static void unpackLibrary(File output, byte[] data) throws IOException
    {
        if (output.exists())
        {
            output.delete();
        }

        byte[] decompressed = DownloadUtils.readFully(new XZInputStream(new ByteArrayInputStream(data)));
        
        //Snag the checksum signature
        String end = new String(decompressed, decompressed.length - 4, 4);
        if (!end.equals("SIGN"))
        {
            System.out.println("Unpacking failed, signature missing " + end);
            return;
        }

        int x = decompressed.length;
        int len =
                ((decompressed[x - 8] & 0xFF)      ) |
                ((decompressed[x - 7] & 0xFF) << 8 ) |
                ((decompressed[x - 6] & 0xFF) << 16) |
                ((decompressed[x - 5] & 0xFF) << 24);
        byte[] checksums = Arrays.copyOfRange(decompressed, decompressed.length - len - 8, decompressed.length - 8);

        FileOutputStream jarBytes = new FileOutputStream(output);
        JarOutputStream jos = new JarOutputStream(jarBytes);

        Pack200.newUnpacker().unpack(new ByteArrayInputStream(decompressed), jos);

        jos.putNextEntry(new JarEntry("checksums.sha1"));
        jos.write(checksums);
        jos.closeEntry();

        jos.close();
        jarBytes.close();
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
                    String target = e[1];
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
            if (!libURL.endsWith(PACK_NAME))
            {
                fnf.printStackTrace();
            }
            return false;
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
