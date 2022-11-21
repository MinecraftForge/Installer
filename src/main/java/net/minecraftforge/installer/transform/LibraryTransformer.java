/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.transform;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;

import argo.jdom.JdomParser;
import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;

public class LibraryTransformer {

    private static final String MC_HOME = "C:/Users/Lex/AppData/Roaming/.minecraft";
    private static final boolean DEBUG = false;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java net.minecraftforge.installer.LibraryTransformer <json1> [json2...]");
            System.out.println("This program parse all passed in json files and remap specified jar files");
            return;
        }

        for (String path : args)
            (new LibraryTransformer()).process(new File(path));
    }

    private void process(File json) {
        try {
            JsonRootNode root = new JdomParser().parse(new InputStreamReader(new FileInputStream(json), Charsets.UTF_8));
            String mcver = root.getStringValue("install", "minecraft");
            TransformInfo[] info = read(root);
            for (TransformInfo ti : info) {
                transform(ti, "client", new File(MC_HOME), mcver);
                transform(ti, "server", new File(MC_HOME), mcver);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public TransformInfo[] read(JsonRootNode json)
    {
        List<TransformInfo> ret = new ArrayList<TransformInfo>();
        JsonNode data = json.getNode("install");
        if (data.isArrayNode("transform"))
        {
            for (JsonNode t : data.getArrayNode("transform"))
            {
                TransformInfo ti = new TransformInfo(t);
                if (!ti.isValid())
                {
                    // Make this more prominent to the packer?
                    System.out.println("Transformed Library is invalid, must specify input, output, and map");
                    continue;
                }
                ret.add(ti);
            }
        }

        return ret.toArray(new TransformInfo[ret.size()]);
    }

    public boolean transform(TransformInfo info, String side, File minecraftHome, String minecraftVersion)
    {
        if (!info.validSide(side)) {
            System.out.println("Skipping " + info.input + " invalid side " + side);
            return true;
        }

        File libraries = new File(minecraftHome, "libraries");
        System.out.println("Processing Transform:");

        File input = null;
        if ("{minecraft_jar}".equals(info.input))
            input = new File(minecraftHome, "versions/" + minecraftVersion + "/" + minecraftVersion + ".jar");
        if ("{minecraft_server_jar}".equals(info.input))
            input = new File(minecraftHome, "minecraft_server." + minecraftVersion + ".jar");
        if (input == null)
            input = info.getInputArtifact().getLocalPath(libraries);

        File output = info.output.getLocalPath(libraries);
        SrgFile srg = new SrgFile(info.map);

        System.out.println("  Input: " + input.toString());
        System.out.println("  Output: " + output.toString());
        System.out.println("  Map: " + srg.toString());

        if (DEBUG) {
            if (output.exists())
                output.delete();
        }
        if (output.exists()) {
            System.out.println("  Skipping as output already exists");
            return true;
        }

        output.getParentFile().mkdirs();

        ZipInputStream inJar = null;
        ZipOutputStream outJar = null;
        boolean errored = false;

        try {
            try {
                inJar = new ZipInputStream(new BufferedInputStream(new FileInputStream(input)));
            } catch (FileNotFoundException e) {
                throw new FileNotFoundException("  Could not open input file: " + e.getMessage());
            }

            outJar = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)));

            while (true) {
                ZipEntry entry = inJar.getNextEntry();

                if (entry == null)
                    break;

                if (entry.isDirectory()) {
                    outJar.putNextEntry(entry);
                    continue;
                }

                byte[] data = new byte[4096];
                ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();

                int len;
                do {
                    len = inJar.read(data);
                    if (len > 0)
                        entryBuffer.write(data, 0, len);
                } while (len != -1);

                byte[] entryData = entryBuffer.toByteArray();

                String entryName = entry.getName();

                if (entryName.endsWith(".class")) {
                    //System.out.println("    Processing " + entryName);

                    ClassReader cr = new ClassReader(entryData);
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    ClassVisitor ca = new ClassRemapper(writer, srg);
                    cr.accept(ca, 0);
                    entryData = writer.toByteArray();
                    entryName = srg.map(entryName.substring(0, entryName.length() - 6)) + ".class";

                    //System.out.println("    Processed " + entryBuffer.size() + " -> " + entryData.length);
                } else {
                    //System.out.println("    Copying " + entryName);
                }

                ZipEntry newEntry = new ZipEntry(entryName);
                newEntry.setTime(entry.getTime());
                newEntry.setSize(entryData.length);
                outJar.putNextEntry(newEntry);
                outJar.write(entryData);
            }
        } catch (IOException e) {
            System.out.println("  Failed to process remapping: " + e.toString());
            e.printStackTrace();
        } finally {
            if (outJar != null) {
                try {
                    outJar.close();
                } catch (IOException e) {}
            }

            if (inJar != null) {
                try {
                    inJar.close();
                } catch (IOException e) { }
            }
        }

        if (errored)
            output.delete();
        return !errored;
    }

}
