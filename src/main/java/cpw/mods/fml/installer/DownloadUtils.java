package cpw.mods.fml.installer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import javax.swing.ProgressMonitor;

import argo.jdom.JsonNode;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

public class DownloadUtils {

    public static int downloadInstalledLibraries(String jsonMarker, File librariesDir, IMonitor monitor, List<JsonNode> libraries, int progress, List<String> grabbed, List<String> bad)
    {
        for (JsonNode library : libraries)
        {
            String libName = library.getStringValue("name");
            monitor.setNote(String.format("Considering library %s", libName));
            if (library.isBooleanValue(jsonMarker) && library.getBooleanValue(jsonMarker))
            {
                String[] nameparts = Iterables.toArray(Splitter.on(':').split(libName), String.class);
                nameparts[0] = nameparts[0].replace('.', '/');
                String jarName = nameparts[1] + '-' + nameparts[2] + ".jar";
                String pathName = nameparts[0] + '/' + nameparts[1] + '/' + nameparts[2] + '/' + jarName;
                File libPath = new File(librariesDir, pathName.replace('/', File.separatorChar));
                String libURL = library.isStringValue("url") ? library.getStringValue("url") + "/" : "https://s3.amazonaws.com/Minecraft.Download/libraries/";
                if (libPath.exists())
                {
                    monitor.setProgress(progress++);
                    continue;
                }
                libPath.getParentFile().mkdirs();
                monitor.setNote(String.format("Downloading library %s", libName));
                libURL += pathName;
                if (!downloadFile(libName, libPath, libURL))
                {
                    bad.add(libName);
                }
                else
                {
                    grabbed.add(libName);
                }
            }
            monitor.setProgress(progress++);
        }
        return progress;
    }

    public static boolean downloadFile(String libName, File libPath, String libURL)
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
    static class URLISSupplier implements InputSupplier<InputStream> {
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
