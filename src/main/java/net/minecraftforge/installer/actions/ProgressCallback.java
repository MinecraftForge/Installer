/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.actions;

import java.io.IOException;
import java.io.OutputStream;

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
        message(label);
    }

    /**
     * Indeterminate progress message, will not care about progress
     */
    default void stage(String message)
    {
        message(message);
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
    void message(String message, MessagePriority priority);

    default void progress(double progress)
    {
        //TODO: Better bar? We're in console.. so let not spam with updates
        //System.out.println(DecimalFormat.getPercentInstance().format(progress));
    }
    
    static ProgressCallback TO_STD_OUT = new ProgressCallback() {

        @Override
        public void message(String message, MessagePriority priority)
        {
            System.out.println(message);
        }
    };
    
    static ProgressCallback withOutputs(OutputStream... streams)
    {
        return new ProgressCallback()
        {
            @Override
            public void message(String message, MessagePriority priority)
            {
                message = message + System.lineSeparator();
                for (OutputStream out : streams)
                {
                    try
                    {
                        out.write(message.getBytes());
                        out.flush();
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }
}
