/*
 * Installer
 * Copyright (c) 2016-2018.
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
package net.minecraftforge.installer.json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class OptionalLibrary {
    private String name;
    private String artifact;
    private String maven;

    private boolean client = false;
    private boolean server = false;
    private boolean _default = true;
    private boolean inject = true;
    private String desc;
    private String url;

    public boolean isValid() {
        return this.name != null && this.artifact != null && this.maven != null;
    }

    public String getName()     { return this.name;     }
    public String getArtifact() { return this.artifact; }
    public String getMaven()    { return this.maven;    }
    public boolean isClient()   { return this.client;   }
    public boolean isServer()   { return this.server;   }
    public boolean getDefault() { return this._default; }
    public boolean isInjected() { return this.inject;   }
    public String getDesc()     { return this.desc;     }
    public String getURL()      { return this.url;      }

    public static boolean saveModListJson(File root, File json, List<OptionalLibrary> libs, Predicate<String> filter)
    {
        List<String> artifacts = new ArrayList<>();
        for (OptionalLibrary lib : libs)
        {
            if (filter.test(lib.getArtifact()))
                artifacts.add(lib.getArtifact());
        }

        if (artifacts.size() == 0)
            return true;

        File parent = json.getParentFile();
        if (!parent.exists())
            parent.mkdirs();

        System.out.println("Saving optional modlist to: " + json);

        StringBuilder buf = new StringBuilder();
        buf.append("{\n");
        buf.append("    \"repositoryRoot\": \"").append(root.getAbsolutePath().replace('\\', '/')).append("\",\n");
        buf.append("    \"modRef\": [\n");
        for (int x = 0; x < artifacts.size(); x++)
        {
            buf.append("        \"").append(artifacts.get(x)).append('"');
            if (x < artifacts.size() - 1)
                buf.append(',');
            buf.append('\n');
        }
        buf.append("    ]\n");
        buf.append("}\n");

        try
        {
            Files.write(json.toPath(), buf.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }
    }
}
