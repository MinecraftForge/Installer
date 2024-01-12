/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.actions;

import java.io.File;
import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.InstallV1;

public class ExtractAction extends Action {
    public ExtractAction(InstallV1 profile, ProgressCallback monitor) {
        super(profile, monitor, true);
    }

    @Override
    public boolean run(File target, File Installer) {
        boolean result = true;
        String failed = "An error occurred extracting the files:";

        Artifact contained = profile.getPath();
        if (contained != null) {
            File file = new File(target, contained.getFilename());

            if (!DownloadUtils.extractFile(contained, file, null)) {
                result = false;
                failed += "\n" + contained.getFilename();
            }
        }

        if (!result)
            error(failed);

        return result;
    }

    @Override
    public boolean isPathValid(File targetDir) {
        return targetDir.exists() && targetDir.isDirectory();
    }

    @Override
    public String getFileError(File targetDir) {
        return !targetDir.exists() ? "Target directory does not exist" : !targetDir.isDirectory() ? "Target is not a directory" : "";
    }

    @Override
    public String getSuccessMessage() {
        return "Extracted successfully";
    }
}
