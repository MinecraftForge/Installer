/*
 * Installer
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.minecraftforge.installer.transform;

import java.util.Locale;

import com.google.common.base.Strings;

import argo.jdom.JsonNode;
import net.minecraftforge.installer.Artifact;

public class TransformInfo {
    public final String side;
    public final String input;
    public final Artifact output;
    public final String map;
    public final boolean append;
    private Artifact inputArtifact;
    public final String maven;

    public TransformInfo(JsonNode node)
    {
        side     = node.isStringValue("side") ? node.getStringValue("side").toUpperCase(Locale.ENGLISH) : null;
        input    = node.getStringValue("input");
        output   = new Artifact(node.getStringValue("output"));
        map      = node.getStringValue("map");
        append   = getBool(node, "append", false);
        maven    = node.isStringValue("maven") ? node.getStringValue("maven") : null;
    }

    private boolean getBool(JsonNode node, String name, boolean _def)
    {
        if (!node.isBooleanValue(name))
            return _def;
        return node.getBooleanValue(name);
    }

    public boolean isValid()
    {
        return !Strings.isNullOrEmpty(input) && output != null && !Strings.isNullOrEmpty(map);
    }

    public Artifact getInputArtifact()
    {
        if (this.inputArtifact == null)
            this.inputArtifact = new Artifact(input);
        return this.inputArtifact;
    }

    public boolean validSide(String side) {
        return side == null || side.toUpperCase(Locale.ENGLISH).equals(this.side);
    }

}
