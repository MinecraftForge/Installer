package net.minecraftforge.installer;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public enum MirrorData {
    INSTANCE;

    private static class Mirror {
        final String name;
        final String imageURL;
        final String clickURL;
        final String url;
        boolean triedImage;
        Icon image;

        public Mirror(String name, String imageURL, String clickURL, String url)
        {
            this.name = name;
            this.imageURL = imageURL;
            this.clickURL = clickURL;
            this.url = url;
        }

        Icon getImage()
        {
            if (!triedImage)
            {
                try
                {
                    image = new ImageIcon(ImageIO.read(new URL(imageURL)));
                }
                catch (Exception e)
                {
                    image = null;
                }
                finally
                {
                    triedImage = true;
                }
            }
            return image;
        }
    }

    private final List<Mirror> mirrors;
    private int chosenMirror;

    private MirrorData()
    {
        if (VersionInfo.hasMirrors())
        {
            mirrors = buildMirrorList();
            if (!mirrors.isEmpty())
            {
                chosenMirror = new Random().nextInt(getAllMirrors().size());
            }
        }
        else
        {
            mirrors = Collections.emptyList();
        }
    }

    private List<Mirror> buildMirrorList()
    {
        String url = VersionInfo.getMirrorListURL();
        List<Mirror> results = Lists.newArrayList();
        List<String> mirrorList = DownloadUtils.downloadList(url);
        Splitter splitter = Splitter.on('!').trimResults();
        for (String mirror : mirrorList)
        {
            String[] strings = Iterables.toArray(splitter.split(mirror),String.class);
            Mirror m = new Mirror(strings[0],strings[1],strings[2],strings[3]);
            results.add(m);
        }
        return results;
    }

    public boolean hasMirrors()
    {
        return VersionInfo.hasMirrors() && mirrors != null && !mirrors.isEmpty();
    }

    private List<Mirror> getAllMirrors()
    {
        return mirrors;
    }

    private Mirror getChosen()
    {
        return getAllMirrors().get(chosenMirror);
    }

    public String getMirrorURL()
    {
        return getChosen().url;
    }

    public String getSponsorName()
    {
        return getChosen().name;
    }

    public String getSponsorURL()
    {
        return getChosen().clickURL;
    }

    public Icon getImageIcon()
    {
        return getChosen().getImage();
    }
}
