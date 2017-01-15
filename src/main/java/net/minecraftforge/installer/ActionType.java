package net.minecraftforge.installer;

import java.io.File;

import com.google.common.base.Predicate;

public interface ActionType {
    boolean run(File target, Predicate<String> optionals);
    boolean isPathValid(File targetDir);
    String getFileError(File targetDir);
    String getSuccessMessage();
    String getSponsorMessage();
    void closeMonitor();
}
