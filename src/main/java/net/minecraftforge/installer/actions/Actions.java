/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.actions;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import net.minecraftforge.installer.json.InstallV1;

public enum Actions
{
    CLIENT("Install client", "Install a new profile to the Mojang client launcher", ClientInstall::new, () -> "Successfully installed client into launcher."),
    SERVER("Install server", "Create a new modded server installation", ServerInstall::new, () -> "The server installed successfully"),
    EXTRACT("Extract", "Extract the contained jar file", ExtractAction::new, () -> "All files successfully extract.");

    private String label;
    private String tooltip;
    private BiFunction<InstallV1, ProgressCallback, Action> action;
    private Supplier<String> success;

    private Actions(String label, String tooltip, BiFunction<InstallV1, ProgressCallback, Action> action, Supplier<String> success)
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

    public String getSuccess()
    {
        return success.get();
    }

    public Action getAction(InstallV1 profile, ProgressCallback monitor) {
        return action.apply(profile, monitor);
    }
}
