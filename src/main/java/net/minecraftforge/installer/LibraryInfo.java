/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import argo.jdom.JsonNode;

public class LibraryInfo
{
    private Artifact artifact;
    private List<String> checksums;
    private boolean side = false;
    private boolean enabled = true;
    private String  url = DownloadUtils.LIBRARIES_URL;

    public LibraryInfo(JsonNode node, String marker)
    {
        this.artifact = new Artifact(node.getStringValue("name"));

        if (node.isArrayNode("checksums"))
        {
            checksums = Lists.newArrayList(Lists.transform(node.getArrayNode("checksums"), new Function<JsonNode, String>()
            {
                @Override
                public String apply(JsonNode node)
                {
                    return node.getText();
                }
            }));
        }
        this.side = node.isBooleanValue(marker) && node.getBooleanValue(marker);

        if (MirrorData.INSTANCE.hasMirrors() && node.isStringValue("url"))
            url = MirrorData.INSTANCE.getMirrorURL();
        else if (node.isStringValue("url"))
            url = node.getStringValue("url") + "/";
    }

    public LibraryInfo(OptionalLibrary lib, String marker)
    {
        this.artifact = new Artifact(lib.getArtifact());
        this.side = (lib.isServer() && "serverreq".equals(marker)) ||
                    (lib.isClient() && "clientreq".equals(marker));
        this.url = lib.getMaven();
    }

    public Artifact     getArtifact()   { return this.artifact;  }
    public List<String> getChecksums()  { return this.checksums; }
    public boolean      isCorrectSide() { return this.side;      }
    public boolean      isEnabled()     { return this.enabled;   }
    public void setEnabled(boolean v)   { this.enabled = v;      }
    public String       getURL()        { return this.url;       }
}