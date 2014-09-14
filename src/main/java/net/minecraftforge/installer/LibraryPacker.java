package net.minecraftforge.installer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Packer;
import java.util.zip.ZipEntry;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;

public class LibraryPacker
{
    private static final boolean DEBUG_SAVE_STAGES = false;
    private static final ArrayList<String> CHECKSUMS = new ArrayList<String>();

    private static class Stopwatch
    {
        public void start(){
            if (fIsRunning) throw new IllegalStateException("Must stop before calling start again.");
            fStart = System.currentTimeMillis();
            fStop = 0;
            fIsRunning = true;
        }
        public void stop() {
            if (!fIsRunning) throw new IllegalStateException("Cannot stop if not currently running.");
            fStop = System.currentTimeMillis();
            fIsRunning = false;
        }

        @Override
        public String toString(){ return length() + " ms"; }
        public long length() { return fStop - fStart; }
        private long fStart;
        private long fStop;

        private boolean fIsRunning;
    }

    public static void main(String[] args) throws IOException
    {
        if (args.length < 1)
        {
            System.out.println("Usage: java net.minecraftforge.installer.LibraryPacker <path1> [path2...]");
            System.out.println("This program will walk the supplied paths recursivly and create compressed versions of any .jar file they find.");
            return;
        }

        for (String path : args)
        {
            walk(new File(path));
        }
    }

    private static void walk(File path) throws IOException
    {
        if (path.isDirectory())
        {
            for (File child : path.listFiles())
            {
                walk(child);
            }
        }
        else if (path.getName().endsWith(".jar"))
        {
            byte[] xz = compress(path);
            //System.exit(0);
            decompress(path, xz, CHECKSUMS);
        }
    }

    private static byte[] compress(File path) throws IOException
    {
        File lzma = new File(path.getAbsolutePath() + ".pack.lzma");
        if (lzma.exists())
        {
            System.out.println("Skipping \"" + path.getAbsolutePath() + "\" lzma already exists");
            return null;
        }

        System.out.println("Processing: " + path.getAbsolutePath());
        byte[] raw = Files.toByteArray(path);
        System.out.println("  Raw:        " + raw.length);
        System.out.println("  SHA1:       " + Hashing.sha1().hashBytes(raw).toString());
        byte[] packed = pack(raw, path);
        System.out.println("  Packed:     " + packed.length);
        byte[] unpacked = unpack(packed, path);
        System.out.println("  Unpacked:   " + unpacked.length);
        byte[] checksums = checksum(unpacked, path);
        System.out.println("  SHA1:       " + Hashing.sha1().hashBytes(checksums).toString());
        CHECKSUMS.add(Hashing.sha1().hashBytes(checksums).toString());
        byte[] xzed = xz(packed, checksums, path);
        System.out.println("  XZed:       " + xzed.length);
        System.out.println("");
        return xzed;
    }

    private static byte[] checksum(byte[] raw, File path) throws IOException
    {
        JarInputStream in = new JarInputStream(new ByteArrayInputStream(raw));

        StringBuffer checksums = new StringBuffer();

        JarEntry entry = in.getNextJarEntry();
        while (entry != null)
        {
            if (!entry.isDirectory())
            {
                checksums.append(Hashing.sha1().hashBytes(DownloadUtils.readFully(in)).toString()).append(' ').append(entry.getName()).append('\n');
            }
            entry = in.getNextJarEntry();
        }
        in.close();

        return checksums.toString().getBytes(Charset.forName("UTF-8"));
    }

    private static final OutputStream NULL_OUT = new OutputStream()
    {
        @Override
        public void write(int b) throws IOException{}
    };

    private static byte[] pack(byte[] data, File path) throws IOException
    {
        JarInputStream in = new JarInputStream(new ByteArrayInputStream(data))
        {
            @Override
            public ZipEntry getNextEntry() throws IOException
            {
                ZipEntry ret = super.getNextEntry();
                while (ret != null && ret.getName().startsWith("META-INF"))
                {
                    ret = super.getNextEntry();
                }
                return ret;
            }
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Packer packer = Pack200.newPacker();

        SortedMap<String, String> props = packer.properties();
        props.put(Packer.EFFORT, "9");
        props.put(Packer.KEEP_FILE_ORDER, Packer.TRUE);
        props.put(Packer.UNKNOWN_ATTRIBUTE, Packer.PASS);

        final PrintStream err = new PrintStream(System.err);
        System.setErr(new PrintStream(NULL_OUT));
        packer.pack(in, out);
        System.setErr(err);

        in.close();
        out.close();

        byte[] packed = out.toByteArray();
        if (DEBUG_SAVE_STAGES)
        {
            Files.write(packed, new File(path.getAbsolutePath() + ".pack"));
        }
        return packed;
    }

    private static byte[] unpack(byte[] data, File path) throws IOException
    {
        File output = new File(path.getAbsolutePath() + ".unpacked");
        if (output.exists())
        {
            output.delete();
        }

        FileOutputStream jar = new FileOutputStream(output);
        JarOutputStream jos = new JarOutputStream(jar);
        Pack200.newUnpacker().unpack(new ByteArrayInputStream(data), jos);
        jos.close();
        jar.close();

        byte[] unpacked = Files.toByteArray(output);

        if (!DEBUG_SAVE_STAGES)
        {
            output.delete();
        }

        return unpacked;
    }

    private static byte[] xz(byte[] data, byte[] checksums, File path) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        LZMA2Options options = new LZMA2Options();
        options.setPreset(8);
        XZOutputStream xz = new XZOutputStream(out, options);
        int x = checksums.length;
        xz.write(data);
        xz.write(checksums);
        xz.write(new byte[]{
                (byte) (x & 0x000000FF),
                (byte)((x & 0x0000FF00) >> 8),
                (byte)((x & 0x00FF0000) >> 16),
                (byte)((x & 0xFF000000) >> 24)
        });
        xz.write("SIGN".getBytes()); //Add our sign to validate
        xz.close();

        byte[] xzed = out.toByteArray();
        Files.write(xzed, new File(path.getAbsolutePath() + ".pack.xz"));
        return xzed;
    }

    private static void decompress(File path, byte[] data, List<String> checksum) throws IOException
    {
        Stopwatch t = new Stopwatch();
        t.start();

        File output = new File(path.getAbsolutePath() + ".unpacked.test");

        DownloadUtils.unpackLibrary(new File(path.getAbsolutePath() + ".unpacked.test"), data);

        DownloadUtils.validateJar(output, Files.toByteArray(output), CHECKSUMS);

        t.stop();
        System.out.println("  Decompress: " + t.toString());
    }


}
