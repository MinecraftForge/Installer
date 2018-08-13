package net.minecraftforge.installer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import argo.jdom.JdomParser;
import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.OutputSupplier;

public class VersionInfo {
    public static final VersionInfo INSTANCE = new VersionInfo();
    public final JsonRootNode versionData;
    private final List<OptionalLibrary> optionals = Lists.newArrayList();

    public VersionInfo()
    {
        InputStream installProfile = getClass().getResourceAsStream("/install_profile.json");
        JdomParser parser = new JdomParser();

        try
        {
            versionData = parser.parse(new InputStreamReader(installProfile, Charsets.UTF_8));

            if (versionData.isArrayNode("optionals"))
            {
                for (JsonNode opt : versionData.getArrayNode("optionals"))
                {
                    OptionalLibrary o = new OptionalLibrary(opt);
                    if (!o.isValid())
                    {
                        // Make this more prominent to the packer?
                        System.out.println("Optional Library is invalid, must specify a name, artifact and maven");
                        continue;
                    }
                    optionals.add(o);
                }
            }
        }
        catch (Exception e)
        {
            throw Throwables.propagate(e);
        }
    }

    public static String getProfileName()
    {
        return INSTANCE.versionData.getStringValue("install","profileName");
    }

    public static String getVersionTarget()
    {
        return INSTANCE.versionData.getStringValue("install","target");
    }
    public static File getLibraryPath(File root)
    {
        String path = INSTANCE.versionData.getStringValue("install","path");
        String[] split = Iterables.toArray(Splitter.on(':').omitEmptyStrings().split(path), String.class);
        File dest = root;
        Iterable<String> subSplit = Splitter.on('.').omitEmptyStrings().split(split[0]);
        for (String part : subSplit)
        {
            dest = new File(dest, part);
        }
        dest = new File(new File(dest, split[1]), split[2]);
        String fileName = split[1]+"-"+split[2]+".jar";
        return new File(dest,fileName);
    }

    public static String getModListType()
    {
        return !INSTANCE.versionData.isStringValue("install", "modList") ? "" :
                INSTANCE.versionData.getStringValue("install", "modList");
    }

    public static String getVersion()
    {
        return INSTANCE.versionData.getStringValue("install","version");
    }

    public static String getWelcomeMessage()
    {
        return INSTANCE.versionData.getStringValue("install","welcome");
    }

    public static String getLogoFileName()
    {
        return INSTANCE.versionData.getStringValue("install","logo");
    }

    public static String getURLFileName()
    {
        if (!INSTANCE.versionData.isStringValue("install", "urlIcon"))
            return "/url.png";
        return INSTANCE.versionData.getStringValue("install", "urlIcon");
    }

    public static boolean getStripMetaInf()
    {
        try
        {
            return INSTANCE.versionData.getBooleanValue("install", "stripMeta");
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static JsonNode getVersionInfo()
    {
        return INSTANCE.versionData.getNode("versionInfo");
    }

    public static File getMinecraftFile(File path)
    {
        return new File(new File(path, getMinecraftVersion()),getMinecraftVersion()+".jar");
    }
    public static String getContainedFile()
    {
        return INSTANCE.versionData.getStringValue("install","filePath");
    }
    public static void extractFile(File path) throws IOException
    {
        INSTANCE.doFileExtract(path);
    }

    private void doFileExtract(File path) throws IOException
    {
        if (Strings.isNullOrEmpty(getContainedFile())) return;
        System.out.println("Extracting: /" + getContainedFile());
        System.out.println("To:          " + path.getAbsolutePath());
        InputStream inputStream = getClass().getResourceAsStream("/"+getContainedFile());
        OutputSupplier<FileOutputStream> outputSupplier = Files.newOutputStreamSupplier(path);
        ByteStreams.copy(inputStream, outputSupplier);
    }

    public static String getMinecraftVersion()
    {
        return INSTANCE.versionData.getStringValue("install","minecraft");
    }

    public static String getMirrorListURL()
    {
        return INSTANCE.versionData.getStringValue("install","mirrorList");
    }

    public static boolean hasMirrors()
    {
        return INSTANCE.versionData.isStringValue("install","mirrorList");
    }

    public static boolean hideClient()
    {
        return INSTANCE.versionData.isBooleanValue("install", "hideClient") &&
                INSTANCE.versionData.getBooleanValue("install", "hideClient");
    }

    public static boolean hideServer()
    {
        return INSTANCE.versionData.isBooleanValue("install", "hideServer") &&
                INSTANCE.versionData.getBooleanValue("install", "hideServer");
    }

    public static boolean hideExtract()
    {
        return INSTANCE.versionData.isBooleanValue("install", "hideExtract") &&
                INSTANCE.versionData.getBooleanValue("install", "hideExtract");
    }

    public static boolean isInheritedJson()
    {
        return INSTANCE.versionData.isStringValue("versionInfo", "inheritsFrom") &&
                INSTANCE.versionData.isStringValue("versionInfo", "jar");
    }

    public static boolean hasOptionals()
    {
        return getOptionals().size() > 0;
    }

    public static List<OptionalLibrary> getOptionals()
    {
        return INSTANCE.optionals;
    }

    public static List<LibraryInfo> getLibraries(String marker, Predicate<String> filter)
    {
        List<LibraryInfo> ret = Lists.newArrayList();

        for (JsonNode node : INSTANCE.versionData.getArrayNode("versionInfo", "libraries"))
            ret.add(new LibraryInfo(node, marker));

        for (OptionalLibrary opt : getOptionals())
        {
            LibraryInfo info = new LibraryInfo(opt, marker);
            info.setEnabled(filter.apply(opt.getArtifact()));
            ret.add(info);
        }

        return ret;
    }
}
