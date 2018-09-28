package net.minecraftforge.installer.actions;

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

    void start(String label);

    /**
     * Indeterminate progress message, will not care about progress
     */
    void stage(String message);

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
    void message(String message, MessagePriority priority);

    void progress(double progress);

}
