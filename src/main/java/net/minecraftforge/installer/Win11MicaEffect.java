/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import javax.swing.JDialog;
import java.awt.Component;

/**
 * No-op implementation for Java 8. See the installer-java22 subproject for the implementation.
 */
final class Win11MicaEffect {
    private Win11MicaEffect() {}

    static void prepare(Component component) {}

    static void install(JDialog dialog) throws Exception {}

    static boolean isSupported() {
        return false;
    }
}
