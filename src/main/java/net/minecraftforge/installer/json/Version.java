/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.installer.json;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Version {
    private String id;
    private Map<String, Download> downloads;
    private Library[] libraries;

    public String getId() {
        return id;
    }

    public Download getDownload(String key) {
        return downloads == null ? null : downloads.get(key);
    }

    public Library[] getLibraries() {
        return libraries == null ? new Library[0] : libraries;
    }

    public static class Download {
        private String sha1;
        private int size;
        private String url;
        private boolean provided = false;

        public String getSha1() {
            return sha1;
        }

        public int getSize() {
            return size;
        }

        public String getUrl() {
            return url == null || provided ? "" : url;
        }

        public boolean getProvided() {
            return provided;
        }
    }

    public static class LibraryDownload extends Download {
        private String path;

        public String getPath() {
            return path;
        }

        public void setPath(String value) {
            this.path = value;
        }
    }

    public static class Library {
        private Artifact name;
        private Downloads downloads;

        public Artifact getName() {
            return name;
        }

        public Downloads getDownloads() {
            return downloads;
        }
    }

    public static class Downloads {
        private LibraryDownload artifact;
        private Map<String, LibraryDownload> classifiers;

        public LibraryDownload getArtifact() {
            return artifact;
        }

        public Set<String> getClassifiers() {
            return classifiers == null ? new HashSet<>() : classifiers.keySet();
        }
    }
}
