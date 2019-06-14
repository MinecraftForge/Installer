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
import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class Artifact {
    //Descriptor parts: group:name:version[:classifier][@extension]
    private String domain;
    private String name;
    private String version;
    private String classifier = null;
    private String ext = "jar";

    //Caches so we don't rebuild every time we're asked.
    private String path;
    private String file;
    private String descriptor;

    public static Artifact from(String descriptor)
    {
        Artifact ret = new Artifact();
        ret.descriptor = descriptor;

        String[] pts = descriptor.split(":");
        ret.domain = pts[0];
        ret.name = pts[1];

        int last = pts.length - 1;
        int idx = pts[last].indexOf('@');
        if (idx != -1) {
            ret.ext = pts[last].substring(idx + 1);
            pts[last] = pts[last].substring(0, idx);
        }

        ret.version = pts[2];
        if (pts.length > 3)
            ret.classifier = pts[3];

        ret.file = ret.name + '-' + ret.version;
        if (ret.classifier != null) ret.file += '-' + ret.classifier;
        ret.file += '.' + ret.ext;

        ret.path = ret.domain.replace('.', '/') + '/' + ret.name + '/' + ret.version + '/' + ret.file;

        return ret;
    }

    public File getLocalPath(File base) {
        return new File(base, path.replace('/', File.separatorChar));
    }

    public String getDescriptor(){ return descriptor; }
    public String getPath()      { return path;       }
    public String getDomain()    { return domain;     }
    public String getName()      { return name;       }
    public String getVersion()   { return version;    }
    public String getClassifier(){ return classifier; }
    public String getExt()       { return ext;        }
    public String getFilename()  { return file;       }
    @Override
    public String toString() {
        return getDescriptor();
    }

    public static class Adapter implements JsonDeserializer<Artifact>, JsonSerializer<Artifact> {
        @Override
        public JsonElement serialize(Artifact src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.getDescriptor());
        }

        @Override
        public Artifact deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return json.isJsonPrimitive() ? Artifact.from(json.getAsJsonPrimitive().getAsString()) : null;
        }
    }
}
