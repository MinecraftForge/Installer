package cpw.mods.fml.installer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import javax.swing.JOptionPane;

import argo.format.PrettyJsonFormatter;
import argo.jdom.JdomParser;
import argo.jdom.JsonField;
import argo.jdom.JsonNode;
import argo.jdom.JsonNodeFactories;
import argo.jdom.JsonRootNode;
import argo.jdom.JsonStringNode;
import argo.saj.InvalidSyntaxException;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

public class ClientInstall implements ActionType {


    @Override
    public void run(File target)
    {
        if (!target.exists())
        {
            JOptionPane.showMessageDialog(null, "There is no minecraft installation at this location!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        File launcherProfiles = new File(target,"launcher_profiles.json");
        if (!launcherProfiles.exists())
        {
            JOptionPane.showMessageDialog(null, "There is no minecraft launcher profile at this location, you need to run the launcher first!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File versionRootDir = new File(target,"versions");
        File versionTarget = new File(versionRootDir,VersionInfo.getVersionTarget());
        if (!versionTarget.mkdirs() && !versionTarget.isDirectory())
        {
            if (!versionTarget.delete())
            {
                JOptionPane.showMessageDialog(null, "There was a problem with the launcher version data. You will need to clear "+versionTarget.getAbsolutePath()+" manually", "Error", JOptionPane.ERROR_MESSAGE);

            }
            else
            {
                versionTarget.mkdirs();
            }
        }

        File versionJsonFile = new File(versionTarget,VersionInfo.getVersionTarget()+".json");
        File clientJarFile = new File(versionTarget, VersionInfo.getVersionTarget()+".jar");
        File minecraftJarFile = VersionInfo.getMinecraftFile(versionRootDir);
        try
        {
            Files.copy(minecraftJarFile, clientJarFile);
        }
        catch (IOException e1)
        {
            JOptionPane.showMessageDialog(null, "You need to run the version "+VersionInfo.getMinecraftVersion()+" manually at least once", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        File targetLibraryFile = VersionInfo.getLibraryPath(new File(target,"libraries"));
        if (!targetLibraryFile.getParentFile().mkdirs() && !targetLibraryFile.getParentFile().isDirectory())
        {
            if (!targetLibraryFile.getParentFile().delete())
            {
                JOptionPane.showMessageDialog(null, "There was a problem with the launcher version data. You will need to clear "+targetLibraryFile.getAbsolutePath()+" manually", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            else
            {
                targetLibraryFile.getParentFile().mkdirs();
            }
        }


        JsonRootNode versionJson = JsonNodeFactories.object(VersionInfo.getVersionInfo().getFields());

        try
        {
            BufferedWriter newWriter = Files.newWriter(versionJsonFile, Charsets.UTF_8);
            PrettyJsonFormatter.fieldOrderPreservingPrettyJsonFormatter().format(versionJson,newWriter);
            newWriter.close();
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(null, "There was a problem writing the launcher version data,  is it write protected?", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try
        {
            VersionInfo.extractFile(targetLibraryFile);
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(null, "There was a problem writing the system library file", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JdomParser parser = new JdomParser();
        JsonRootNode jsonProfileData;

        try
        {
            jsonProfileData = parser.parse(Files.newReader(launcherProfiles, Charsets.UTF_8));
        }
        catch (InvalidSyntaxException e)
        {
            JOptionPane.showMessageDialog(null, "The launcher profile file is corrupted. Re-run the minecraft launcher to fix it!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        catch (Exception e)
        {
            throw Throwables.propagate(e);
        }


        JsonField[] fields = new JsonField[] {
            JsonNodeFactories.field("name", JsonNodeFactories.string(VersionInfo.getProfileName())),
            JsonNodeFactories.field("lastVersionId", JsonNodeFactories.string(VersionInfo.getVersionTarget())),
        };

        HashMap<JsonStringNode, JsonNode> profileCopy = Maps.newHashMap(jsonProfileData.getNode("profiles").getFields());
        HashMap<JsonStringNode, JsonNode> rootCopy = Maps.newHashMap(jsonProfileData.getFields());
        profileCopy.put(JsonNodeFactories.string(VersionInfo.getProfileName()), JsonNodeFactories.object(fields));
        JsonRootNode profileJsonCopy = JsonNodeFactories.object(profileCopy);

        rootCopy.put(JsonNodeFactories.string("profiles"), profileJsonCopy);

        jsonProfileData = JsonNodeFactories.object(rootCopy);

        try
        {
            BufferedWriter newWriter = Files.newWriter(launcherProfiles, Charsets.UTF_8);
            PrettyJsonFormatter.fieldOrderPreservingPrettyJsonFormatter().format(jsonProfileData,newWriter);
            newWriter.close();
        }
        catch (Exception e)
        {
            JOptionPane.showMessageDialog(null, "There was a problem writing the launch profile,  is it write protected?", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }


    }

    @Override
    public boolean isPathValid(File targetDir)
    {
        if (targetDir.exists())
        {
            File launcherProfiles = new File(targetDir,"launcher_profiles.json");
            return launcherProfiles.exists();
        }
        return false;
    }


    public String getFileError(File targetDir)
    {
        if (targetDir.exists())
        {
            return "The directory is missing a launcher profile. Please run the minecraft launcher first";
        }
        else
        {
            return "There is no minecraft directory set up. Either choose an alternative, or run the minecraft launcher to create one";
        }
    }
}
