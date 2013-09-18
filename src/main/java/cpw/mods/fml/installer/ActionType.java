package cpw.mods.fml.installer;

import java.io.File;

import javax.swing.ImageIcon;

public interface ActionType {
    boolean run(File target);
    boolean isPathValid(File targetDir);
    String getFileError(File targetDir);
    String getSuccessMessage();
    String getSponsorMessage();
}
