package net.minecraftforge.installer.json;

import java.util.Random;

import net.minecraftforge.installer.DownloadUtils;

public class Install
{
    // Profile name to install and direct at this new version
    private String profile;
    // Version name to install to.
    private String version;
    // Vanilla version this is based off of.
    private String minecraft;
    // Version json to install into the client
    private String json;
    // Logo to be displayed on the installer GUI.
    private String logo;
    // Maven artifact path for the 'main' jar to install.
    private String path;
    // Icon to use for the url button
    private String urlIcon;
    // Welcome message displayed on main install panel.
    private String welcome;
    // URL for mirror list, which needs to be a json file in the format of an array of Mirror
    private String mirrorList;
    //Hides an entry from the install UI
    private boolean hideClient, hideServer, hideExtract = false;


    // Unserialized values
    private Mirror mirror;
    private boolean triedMirrors = false;

    public String getProfile() {
        return profile;
    }

    public String getVersion() {
        return version;
    }

    public String getMinecraft() {
        return minecraft;
    }

    public String getJson() {
        return json;
    }

    public String getLogo() {
        return logo;
    }

    public Artifact getPath() {
        return new Artifact(path);
    }

    public String getUrlIcon() {
        return urlIcon == null ? "/url.png" : urlIcon;
    }

    public String getWelcome() {
        return welcome == null ? "" : welcome;
    }

    public String getMirrorList() {
        return mirrorList;
    }

    public Mirror getMirror() {
        if (getMirrorList() == null)
            return null;
        if (!triedMirrors && mirror == null) {
            Mirror[] list = DownloadUtils.downloadMirrors(getMirrorList());
            mirror = list == null ? null : list[new Random().nextInt(list.length)];
        }
        return mirror;
    }

    public boolean hideClient() {
        return hideClient;
    }

    public boolean hideServer() {
        return hideServer;
    }

    public boolean hideExtract() {
        return hideExtract;
    }
}
