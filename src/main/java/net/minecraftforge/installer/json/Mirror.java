/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.json;

import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

public class Mirror {
    private String name;
    private String image;
    private String homepage;
    private String url;
    private boolean triedImage;
    private Icon _image_;

    public Mirror() {}

    public Mirror(String name, String image, String homepage, String url) {
        this.name = name;
        this.image = image;
        this.homepage = homepage;
        this.url = url;
    }

    public Icon getImage() {
        if (!triedImage) {
            try {
                if (getImageAddress() != null)
                    _image_ = new ImageIcon(ImageIO.read(new URL(getImageAddress())));
            } catch (Exception e) {
                _image_ = null;
            } finally {
                triedImage = true;
            }
        }
        return _image_;
    }

    public String getName() {
        return name;
    }
    public String getImageAddress() {
        return image;
    }
    public String getHomepage() {
        return homepage;
    }
    public String getUrl() {
        return url;
    }
}
