/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

public interface IMonitor {
    void setMaximum(int max);
    void setNote(String note);
    void setProgress(int progress);
    void close();
}
