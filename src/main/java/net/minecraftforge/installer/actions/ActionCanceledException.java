/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.actions;

public class ActionCanceledException extends Exception
{
    private static final long serialVersionUID = 1L;

    ActionCanceledException(Exception parent)
    {
        super(parent);
    }
}
