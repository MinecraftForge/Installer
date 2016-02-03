package net.minecraftforge.installer;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import argo.jdom.JsonNode;

public class ServerInstall implements ActionType {

    public static boolean headless;
    private List<Artifact> grabbed;

    @Override
    public boolean run(File target)
    {
        if (target.exists() && !target.isDirectory())
        {
            if (!headless)
            	ActionType.error("There is a file at this location, the server cannot be installed here!");
            return false;
        }

        File librariesDir = new File(target,"libraries");
        if (!target.exists())
        {
            target.mkdirs();
        }
        librariesDir.mkdir();
        List<JsonNode> libraries = VersionInfo.getVersionInfo().getArrayNode("libraries");
        int progress = 2;
        grabbed = Lists.newArrayList();
        List<Artifact> bad = Lists.newArrayList();

        //Download MC Server jar
        String mcServerURL = String.format(DownloadUtils.VERSION_URL_SERVER.replace("{MCVER}", VersionInfo.getMinecraftVersion()));
        File mcServerFile = new File(target,"minecraft_server."+VersionInfo.getMinecraftVersion()+".jar");
        if (!mcServerFile.exists())
        {
            if (!DownloadUtils.downloadFileEtag("minecraft server", mcServerFile, mcServerURL))
            {
                mcServerFile.delete();
                if (!headless)
                {
                	ActionType.error("Downloading minecraft server failed, invalid e-tag checksum.\n"+
                                                        "Try again, or manually place server jar to skip download.");
                }
                else
                {
                    System.err.println("Downloading minecraft server failed, invalid e-tag checksum.");
                    System.err.println("Try again, or manually place server jar to skip download.");
                }
                return false;
            }
        }
        progress = DownloadUtils.downloadInstalledLibraries("serverreq", librariesDir, /*monitor,*/ libraries, progress, grabbed, bad);
        
        if (bad.size() > 0)
        {
            String list = Joiner.on("\n").join(bad);
            if (!headless)
            	ActionType.error("These libraries failed to download. Try again.\n"+list);
            else
                System.err.println("These libraries failed to download, try again. \n"+list);
            return false;
        }
        try
        {
            File targetRun = new File(target,VersionInfo.getContainedFile());
            VersionInfo.extractFile(targetRun);
        }
        catch (IOException e)
        {
            if (!headless)
            	ActionType.error("An error occurred installing the library");
            else
                System.err.println("An error occurred installing the distributable");
            return false;
        }

        return true;
    }

    @Override
    public boolean isPathValid(File targetDir)
    {
        return targetDir.exists() && targetDir.isDirectory() && targetDir.list().length == 0;
    }

    @Override
    public String getFileError(File targetDir)
    {
        if (!targetDir.exists())
        {
            return "The specified directory does not exist<br/>It will be created";
        }
        else if (!targetDir.isDirectory())
        {
            return "The specified path needs to be a directory";
        }
        else
        {
            return "There are already files at the target directory";
        }
    }

    @Override
    public String getSuccessMessage()
    {
        if (grabbed.size() > 0)
        {
            return String.format("Successfully downloaded minecraft server, downloaded %d libraries and installed %s", grabbed.size(), VersionInfo.getProfileName());
        }
        return String.format("Successfully downloaded minecraft server and installed %s", VersionInfo.getProfileName());
    }

    @Override
    public String getSponsorMessage()
    {
        return MirrorData.INSTANCE.hasMirrors() ? String.format(headless ? "Data kindly mirrored by %2$s at %1$s" : "<html><a href=\'%s\'>Data kindly mirrored by %s</a></html>", MirrorData.INSTANCE.getSponsorURL(),MirrorData.INSTANCE.getSponsorName()) : null;
    }
}
