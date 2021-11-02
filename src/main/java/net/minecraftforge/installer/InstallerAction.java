/*
 * Installer
 * Copyright (c) 2016-2021.
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
package net.minecraftforge.installer;

import java.io.File;
import java.util.function.Supplier;

import javax.swing.Icon;

import com.google.common.base.Predicate;

public enum InstallerAction {
    CLIENT("Install client", "Install a new profile to the Mojang client launcher", ClientInstall::new),
    SERVER("Install server", "Create a new modded server installation", ServerInstall::new),
    EXTRACT("Extract", "Extract the contained jar file", ExtractAction::new);

    private String label;
    private String tooltip;
    private ActionType action;

    private InstallerAction(String label, String tooltip, Supplier<? extends ActionType> action)
    {
        this.label = label;
        this.tooltip = tooltip;
        this.action = action.get();
    }
    public String getButtonLabel()
    {
        return label;
    }

    public String getTooltip()
    {
        return tooltip;
    }

    public boolean run(File path, Predicate<String> optionals)
    {
        return action.run(path, optionals);
    }
    public boolean isPathValid(File targetDir)
    {
        return action.isPathValid(targetDir);
    }

    public String getFileError(File targetDir)
    {
        return action.getFileError(targetDir);
    }
    public String getSuccessMessage()
    {
        return action.getSuccessMessage();
    }
    public String getSponsorMessage()
    {
        return action.getSponsorMessage();
    }

    public Icon getSponsorLogo()
    {
        return MirrorData.INSTANCE.getImageIcon();
    }
    public String getSponsorURL()
    {
        return MirrorData.INSTANCE.getSponsorURL();
    }
}
