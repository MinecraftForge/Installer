package net.minecraftforge.installer.actions;

public class ActionCanceledException extends Exception
{
    private static final long serialVersionUID = 1L;

    ActionCanceledException(Exception parent)
    {
        super(parent);
    }
}
