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

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.objectweb.asm.commons.Remapper;

import com.google.common.io.CharStreams;

public class SrgFile extends Remapper{

    private String path;
    private List<Obf> classes = new ArrayList<Obf>();
    private List<Fd> fields = new ArrayList<Fd>();
    private List<Mtd> methods = new ArrayList<Mtd>();
    private Map<String, String> map = new HashMap<String, String>();

    public SrgFile(String path) {
        this.path = path;

        try {
            List<String> lines = CharStreams.readLines(new InputStreamReader(getClass().getResourceAsStream(this.path)));

            String ext = this.path.substring(this.path.lastIndexOf('.') + 1).toUpperCase(Locale.ENGLISH);
            boolean srg = "SRG".equals(ext);

            Obf current = null;
            for (String line : lines) {
                //Yay comments!
                if (line.indexOf('#') == 0)
                    continue;
                if (line.indexOf('#') > 0) {
                    line = line.substring(0, line.indexOf('#')-1);
                    line = line.replaceFirst("\\s++$", "");
                }
                if(line.isEmpty())
                    continue;

                String[] pts = line.replace('\t', ' ').split(" ");

                if (srg){
                    if ("PK:".equals(pts[0])) { //Skip packages. Should we care?
                    } else if ("CL:".equals(pts[0])) {
                        current = new Obf(pts[1], pts[2]);
                        classes.add(current);
                    } else if ("FD:".equals(pts[0])) {
                        fields.add(new Fd(rsplit(pts[1], '/')[0], rsplit(pts[2], '/')[0], rsplit(pts[1], '/')[1], rsplit(pts[2], '/')[1]));
                    } else if ("MD:".equals(pts[0])) {
                        methods.add(new Mtd(rsplit(pts[1], '/')[0], rsplit(pts[3], '/')[0], rsplit(pts[1], '/')[1], pts[2], rsplit(pts[3], '/')[1], pts[4]));
                    }
                } else {
                    if (pts.length == 2) {
                        current = new Obf(pts[0], pts[1]);
                        classes.add(current);
                    } else if (pts.length == 3) {
                        fields.add(new Fd(pts[0].isEmpty() ? current.obf : pts[0], null, pts[1], pts[2]));
                    } else if (pts.length == 4) {
                        methods.add(new Mtd(pts[0].isEmpty() ? current.obf : pts[0], null, pts[1], pts[2], pts[3], null));
                    }
                }
            }

            for (Obf cls : classes)
                map.put(cls.obf, cls.deobf);

            for (Fd fd : fields) {
                if (fd.cls.deobf == null)
                    fd.cls.deobf = map.get(fd.cls.obf);
                map.put(fd.cls.obf + "/" + fd.obf, /*fd.cls.deobf + "/" +*/ fd.deobf);
            }

            for (Mtd mtd : methods) {
                if (mtd.cls.deobf == null)
                    mtd.cls.deobf = map.get(mtd.cls.obf);
                if (mtd.sig.deobf == null) {
                    StringBuilder buf = new StringBuilder();
                    int start = 0;
                    int l = -1;
                    while ((l = mtd.sig.obf.indexOf('L', start)) != -1) {
                        buf.append(mtd.sig.obf.substring(start, l+1));
                        String cls = mtd.sig.obf.substring(l+1, mtd.sig.obf.indexOf(';', l));
                        cls = map.get(cls) == null ? cls  : map.get(cls);
                        buf.append(cls);
                        start = mtd.sig.obf.indexOf(';', l);
                    }
                    buf.append(mtd.sig.obf.substring(start));
                    mtd.sig.deobf = buf.toString();
                }
                map.put(mtd.cls.obf   + "/" + mtd.obf   + " " + mtd.sig.obf,
                        /*mtd.cls.deobf + "/" +*/ mtd.deobf /*+ " " + mtd.sig.deobf*/);
            }
        } catch (IOException e) {
            System.out.println("Failed to read SRG file: " + this.path);
            e.printStackTrace();
        }
    }

    private String[] rsplit(String data, char chr) {
        return new String[] {
            data.substring(0, data.lastIndexOf(chr) - 1),
            data.substring(data.lastIndexOf(chr) + 1)
        };
    }

    @Override
    public String toString() {
        return path + " CL: " + classes.size() + " FD: " + fields.size() + " MD: " + methods.size();
    }

    // ========================== Remapper =============================
    public String mapMethodName(String owner, String name, String desc) {
        String remap = map.get(owner + "/" + name + " " + desc);
        return remap == null ? name : remap;
    }

    public String mapInvokeDynamicMethodName(String name, String desc) {
        System.out.println("Invoke Dynamic: " + name + " " + desc);
        return name;
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        String remap = map.get(owner + "/" + name); //SRG Fields dont have desc, do we care?
        return remap == null ? name : remap;
    }

    @Override
    public String map(String typeName) {
        String remap = map.get(typeName);
        return remap == null ? typeName : remap;
    }
    // ========================== /Remapper =============================

    private static class Obf {
        protected String obf;
        protected String deobf;

        public Obf(String obf, String deobf) {
            this.obf = obf;
            this.deobf = deobf;
        }
    }

    private static class Fd extends Obf {
        protected Obf cls;
        public Fd(String cls, String clsD, String obf, String deobf) {
            super(obf, deobf);
            this.cls = new Obf(cls, clsD);
        }
    }

    private static class Mtd extends Obf {
        protected Obf cls;
        protected Obf sig;
        public Mtd(String cls, String clsD, String obf, String sig, String deobf, String sigD) {
            super(obf, deobf);
            this.cls = new Obf(cls, clsD);
            this.sig = new Obf(sig, sigD);
        }
    }

}
