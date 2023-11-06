/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.json;

import java.util.List;

public class Manifest {
    private List<Info> versions;

    public String getUrl(String version) {
        return versions == null ? null : versions.stream().filter(v -> version.equals(v.getId())).map(Info::getUrl).findFirst().orElse(null);
    }

    public static class Info {
        private String id;
        private String url;

        public String getId() {
            return id;
        }

        public String getUrl() {
            return url;
        }
    }
}
