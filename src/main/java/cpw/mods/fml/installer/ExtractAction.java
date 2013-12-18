package cpw.mods.fml.installer;

import java.io.File;
import java.io.IOException;

import javax.swing.JOptionPane;

public class ExtractAction implements ActionType {

    public static boolean headless;
    @Override
    public boolean run(File target)
    {
        File file = new File(target,VersionInfo.getContainedFile());
        try
        {
            VersionInfo.extractFile(file);
        }
        catch (IOException e)
        {
            if (!headless)
                LogHandler.logErrorWithDialog("An error occurred extracting file", "Error");
            LogHandler.log.severe("An error occured extracting file");
            return false;
        }
        return true;
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
