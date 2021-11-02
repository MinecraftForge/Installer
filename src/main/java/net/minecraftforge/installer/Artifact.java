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
package net.minecraftforge.installer;

import java.io.File;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class Artifact
{
    private String domain;
    private String name;
    private String version;
    private String classifier = null;
    private String ext = "jar";
    private String path;
    private String file;
    private String descriptor;
    private String memo;

    public Artifact(String descriptor)
    {
        this.descriptor = descriptor;

        String[] pts = Iterables.toArray(Splitter.on(':').split(descriptor), String.class);
        domain = pts[0];
        name = pts[1];

        int last = pts.length - 1;
        int idx = pts[last].indexOf('@');
        if (idx != -1)
        {
            ext = pts[last].substring(idx + 1);
            pts[last] = pts[last].substring(0, idx);
        }

        version = pts[2];
        if (pts.length > 3)
        {
            classifier = pts[3];
        }

        file = name + '-' + version;
        if (classifier != null) file += '-' + classifier;
        file += '.' + ext;

        path = domain.replace('.', '/') + '/' + name + '/' + version + '/' + file;
    }

    public File getLocalPath(File base)
    {
        return new File(base, path.replace('/', File.separatorChar));
    }

    public String getDescriptor(){ return descriptor; }
    public String getPath()      { return path;       }
    public String getDomain()    { return domain;     }
    public String getName()      { return name;       }
    public String getVersion()   { return version;    }
    public String getClassifier(){ return classifier; }
    public String getExt()       { return ext;        }
    public String getMemo()      { return memo;       }
    public void setMemo(String v){ memo = v;          }
    @Override
    public String toString()
    {
        if (getMemo() != null)
            return getDescriptor() + "\n    " + getMemo();
        return getDescriptor();
    }
}
