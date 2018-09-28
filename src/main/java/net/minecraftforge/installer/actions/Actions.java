package net.minecraftforge.installer.actions;

import java.util.function.BiFunction;
import java.util.function.Function;

import javax.swing.ProgressMonitor;

import net.minecraftforge.installer.json.Install;

public enum Actions
{
    CLIENT("Install client", "Install a new profile to the Mojang client launcher", ClientInstall::new, jar -> "Successfully installed client into launcher."),
    SERVER("Install server", "Create a new modded server installation", ServerInstall::new, jar -> "The server installed successfully, you should now be able to run the file " + jar),
    EXTRACT("Extract", "Extract the contained jar file", ExtractAction::new, jar -> "All files successfully extract.");

    private String label;
    private String tooltip;
    private BiFunction<Install, ProgressCallback, Action> action;
    private Function<String, String> success;

    private Actions(String label, String tooltip, BiFunction<Install, ProgressCallback, Action> action, Function<String, String> success)
    {
        this.label = label;
        this.tooltip = tooltip;
        this.success = success;
        this.action = action;
    }

    public String getButtonLabel()
    {
        return label;
    }

    public String getTooltip()
    {
        return tooltip;
    }

    public String getSuccess(String jar)
    {
        return success.apply(jar);
    }

    public Action getAction(Install profile, ProgressCallback monitor) {
        return action.apply(profile, monitor);
    }
}
