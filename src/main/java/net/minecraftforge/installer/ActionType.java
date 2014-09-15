package net.minecraftforge.installer;

import java.io.File;

public interface ActionType {
    boolean run(File target);
    boolean isPathValid(File targetDir);
    String getFileError(File targetDir);
    String getSuccessMessage();
    String getSponsorMessage();
}
