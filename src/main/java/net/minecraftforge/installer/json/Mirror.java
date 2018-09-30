/*
 * Installer
 * Copyright (c) 2016-2018.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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
