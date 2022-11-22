/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.JOptionPane;

import com.google.common.base.Predicate;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.OutputSupplier;

import net.minecraftforge.installer.json.Artifact;

public class ExtractAction implements ActionType {

    public static boolean headless;
    @Override
    public boolean run(File target, Predicate<String> optionals)
    {
        boolean result = true;
        String failed = "An error occurred extracting the files:";

        File file = new File(target,VersionInfo.getContainedFile());
        try
        {
            VersionInfo.extractFile(file);
        }
        catch (IOException e)
        {
            result = false;
            failed += "\n" + VersionInfo.getContainedFile();
        }

        for (OptionalLibrary opt : VersionInfo.getOptionals())
        {
            Artifact art = Artifact.from(opt.getArtifact());
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

        if (!result)
        {
            if (!headless)
                JOptionPane.showMessageDialog(null, failed, "Error", JOptionPane.ERROR_MESSAGE);
            System.out.println(failed);
        }

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
    public String getSuccessMessage()
    {
        return "Extracted successfully";
    }

    @Override
    public String getSponsorMessage()
    {
        return null;
    }
}
