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

/*
 * Changes in v1 of the spec:
 * Add support for an 'extract' task. This simply extracts a file to a specified path.
 * Adds a new value into the processor argument types:
 *    {ROOT} the root directory that we are installing to.
 *    {INSTALLER} the absolute path to the currently running installer.
 *    {MINECRAFT_VERSION} the version number specified in the config.
 *    {LIBRARY_DIR} Path to libraries folder. Typically {ROOT}/libraries/ but can be changed in the future.
 */
public class InstallV1 extends Install {
    /*
     *  The path to install the server jar to, defaults to {ROOT}/minecraft_server.{MINECRAFT_VERSION}.jar
     *  However, this is important to 'hide' the server jar in the libraries folder for Forge 1.17+
     */
    protected String serverJarPath;

    public InstallV1(Install v0) {
        this.profile = v0.profile;
        this.version = v0.version;
        this.icon = v0.icon;
        this.minecraft = v0.minecraft;
        this.json = v0.json;
        this.logo = v0.logo;
        this.path = v0.path;
        this.urlIcon = v0.urlIcon;
        this.welcome = v0.welcome;
        this.mirrorList = v0.mirrorList;
        this.hideClient = v0.hideClient;
        this.hideServer = v0.hideServer;
        this.hideExtract = v0.hideExtract;
        this.libraries = v0.libraries;
        this.processors = v0.processors;
        this.data = v0.data;
    }

    public String getServerJarPath() {
        if (this.serverJarPath == null) {
            return "{ROOT}/minecraft_server.{MINECRAFT_VERSION}.jar";
        }
        return this.serverJarPath;
    }

}
