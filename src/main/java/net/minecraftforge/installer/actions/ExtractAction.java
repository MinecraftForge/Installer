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
package net.minecraftforge.installer.actions;

import java.io.File;
import java.util.function.Predicate;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Install;

public class ExtractAction extends Action {

    public ExtractAction(Install profile, ProgressCallback monitor) {
        super(profile, monitor, true);
    }

    public static boolean headless;
    @Override
    public boolean run(File target, Predicate<String> optionals)
    {
        boolean result = true;
        String failed = "An error occurred extracting the files:";

        Artifact contained = profile.getPath();
        File file = new File(target, contained.getFilename());

        if (!DownloadUtils.extractFile(contained, file, null)) {
            result = false;
            failed += "\n" + contained.getFilename();
        }

        /*
        for (OptionalLibrary opt : VersionInfo.getOptionals())
        {
            Artifact art = new Artifact(opt.getArtifact());
            InputStream input = ExtractAction.class.getResourceAsStream("/maven/" + art.getPath());
            if (input == null)
                continue;

            File path = art.getLocalPath(new File(target, "libraries"));
            File outFolder = art.getLocalPath(path).getParentFile();

            if (!outFolder.exists())
                outFolder.mkdirs();

            OutputSupplier<FileOutputStream> outputSupplier = Files.newOutputStreamSupplier(path);
            try
            {
                ByteStreams.copy(input, outputSupplier);
            }
            catch (IOException e)
            {
                result = false;
                failed += "\n" + opt.getArtifact();
            }
        }
        */

        if (!result)
            error(failed);

        return result;
    }

    @Override
    public boolean isPathValid(File targetDir)
    {
        return targetDir.exists() && targetDir.isDirectory();
    }

    @Override
    public String getFileError(File targetDir)
    {
        return !targetDir.exists() ? "Target directory does not exist" : !targetDir.isDirectory() ? "Target is not a directory" : "";
    }

    @Override
    public String getSuccessMessage() {
        return "Extracted successfully";
    }
}
