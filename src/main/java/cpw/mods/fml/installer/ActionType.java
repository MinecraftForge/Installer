package cpw.mods.fml.installer;

import java.io.File;

public interface ActionType {
    void run(File target);
    boolean isPathValid(File targetDir);
    String getFileError(File targetDir);
}
