package net.minecraftforge.installer.actions;

import java.io.File;
import java.util.function.Predicate;

import javax.swing.JOptionPane;

import net.minecraftforge.installer.IMonitor;
import net.minecraftforge.installer.SimpleInstaller;
import net.minecraftforge.installer.json.Install;

public abstract class Action {
    protected Install profile;
    protected IMonitor monitor;

    protected Action(Install profile) {
        this.profile = profile;
        this.monitor = IMonitor.buildMonitor(); //TODO: Headless argument instead of public field?
    }

    protected void error(String message) {
        if (!SimpleInstaller.headless)
            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        for (String line : message.split("\n"))
            monitor.setNote(line);
    }

    protected void info(String message) {
        for (String line : message.split("\n"))
            monitor.setNote(line);
    }

    public abstract boolean run(File target, Predicate<String> optionals);
    public abstract boolean isPathValid(File targetDir);
    public abstract String getFileError(File targetDir);
    public abstract String getSuccessMessage();

    public String getSponsorMessage() {
        return profile.getMirror() != null ? String.format(SimpleInstaller.headless ? "Data kindly mirrored by %2$s at %1$s" : "<html><a href=\'%s\'>Data kindly mirrored by %s</a></html>", profile.getMirror().getHomepage(), profile.getMirror().getName()) : null;
    }
}
