package cpw.mods.fml.installer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

import argo.jdom.JsonNode;

public class ServerInstall implements ActionType {

    private class URLISSupplier implements InputSupplier<InputStream> {
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

    @Override
    public boolean run(File target)
    {
        if (target.exists() && !target.isDirectory())
        {
            JOptionPane.showMessageDialog(null, "There is a file at this location, the server cannot be installed here!", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        File librariesDir = new File(target,"libraries");
        if (!target.exists())
        {
            target.mkdirs();
            librariesDir.mkdir();
        }
        ProgressMonitor monitor = new ProgressMonitor(null, "Downloading libraries", "Libraries are being analyzed", 0, 1);
        List<JsonNode> libraries = VersionInfo.getVersionInfo().getArrayNode("libraries");
        monitor.setMaximum(libraries.size() + 1);
        int i = 0;
        List<String> skipped = Lists.newArrayList();
        List<String> bad = Lists.newArrayList();
        String mcServerURL = String.format("https://s3.amazonaws.com/Minecraft.Download/versions/%s/minecraft_server.%s.jar", VersionInfo.getMinecraftVersion(), VersionInfo.getMinecraftVersion());
        File mcServerFile = new File(target,"minecraft_server."+VersionInfo.getMinecraftVersion()+".jar");
        if (!mcServerFile.exists())
        {
            monitor.setNote("Considering minecraft server jar");
            downloadFile("minecraft server", mcServerFile, mcServerURL);
        }
        for (JsonNode library : libraries)
        {
            String libName = library.getStringValue("name");
            monitor.setNote(String.format("Considering library %s",libName));
            if (library.isBooleanValue("serverreq") && library.getBooleanValue("serverreq"))
            {
                String[] nameparts = Iterables.toArray(Splitter.on(':').split(libName),String.class);
                nameparts[0].replace('.', '/');
                String jarName = nameparts[1]+'-'+nameparts[2]+".jar";
                String pathName = nameparts[0]+'/'+nameparts[1]+'/'+nameparts[2]+'/'+jarName;
                File libPath = new File(librariesDir,pathName.replace('/', File.pathSeparatorChar));
                String libURL = library.isStringValue("url") ? library.getStringValue("url") : "https://s3.amazonaws.com/Minecraft.Download/libraries/";
                if (libPath.exists())
                {
                    skipped.add(libName);
                    monitor.setProgress(i++);
                    continue;
                }
                libPath.mkdirs();
                monitor.setNote(String.format("Downloading library %s",libName));
                libURL+=pathName;
                if (!downloadFile(libName, libPath, libURL))
                {
                    bad.add(libName);
                }
            }
            monitor.setProgress(i++);
        }

        if (bad.size() > 0)
        {
            String list = Joiner.on(", ").join(bad);
            JOptionPane.showMessageDialog(null, "These libraries failed to download. Try again.\n"+list, "Error downloading", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        try
        {
            File targetRun = new File(target,VersionInfo.INSTANCE.versionData.getStringValue("install","filePath"));
            VersionInfo.extractFile(targetRun);
        }
        catch (IOException e)
        {
            JOptionPane.showMessageDialog(null, "An error occurred installing the library", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    private boolean downloadFile(String libName, File libPath, String libURL)
    {
        try
        {
            URL url = new URL(libURL);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            InputSupplier<InputStream> urlSupplier = new URLISSupplier(connection);
            Files.copy(urlSupplier, libPath);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
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
        // TODO Auto-generated method stub
        return null;
    }

}
