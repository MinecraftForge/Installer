package cpw.mods.fml.installer;

import java.io.File;

import javax.swing.JOptionPane;

public class ServerInstall implements ActionType {

    @Override
    public void run(File target)
    {
        if (target.exists() && !target.isDirectory())
        {
            JOptionPane.showMessageDialog(null, "There is a file at this location, the server cannot be installed here!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!target.exists())
        {
            target.mkdirs();
        }


    }

    @Override
    public boolean isPathValid(File targetDir)
    {
        return targetDir.exists() && targetDir.isDirectory() && targetDir.list().length == 0;
    }

    @Override
    public String getFileError(File targetDir)
    {
        if (!targetDir.exists())
        {
            return "The specified directory does not exist<br/>It will be created";
        }
        else if (!targetDir.isDirectory())
        {
            return "The specified path needs to be a directory";
        }
        else
        {
            return "There are already files at the target directory";
        }
    }

}
