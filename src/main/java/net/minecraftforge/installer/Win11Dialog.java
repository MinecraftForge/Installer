/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * No-op implementation for Java 8. See the installer-java22 subproject for the implementation.
 */
final class Win11Dialog {
    private Win11Dialog() {}

    static JDialog create(JOptionPane optionPane, JPanel installerPanel, String title) throws Exception {
        throw new UnsupportedOperationException("Win11Dialog requires Java 22+");
    }
}
