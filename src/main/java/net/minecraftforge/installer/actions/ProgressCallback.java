package net.minecraftforge.installer.actions;

import java.text.DecimalFormat;

public interface ProgressCallback
{
    enum MessagePriority
    {
        LOW, NORMAL,
        /**
         * Unused so far
         */
        HIGH,
    }

    default void start(String label)
    {
        System.out.println(label);
    }

    /**
     * Indeterminate progress message, will not care about progress
     */
    default void stage(String message)
    {
        System.out.println(message);
    }

    /**
     * @see #message(String, MessagePriority)
     */
    default void message(String message)
    {
        message(message, MessagePriority.NORMAL);
    }

    /**
     * Does not affect indeterminacy or progress, just updates the text (or prints
     * it)
     */
    default void message(String message, MessagePriority priority)
    {
        System.out.println(message);
    }

    default void progress(double progress)
    {
        System.out.println(DecimalFormat.getPercentInstance().format(progress));
    }
}
