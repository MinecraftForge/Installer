/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.json;

public class Spec {
    // Specification for this json format. Current known value is 0, or missing, This is for future use if we ever change the format/functionality of the installer..
    private int spec = 0;

    public int getSpec() {
        return spec;
    }
}
