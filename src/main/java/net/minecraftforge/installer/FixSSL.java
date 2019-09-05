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
package net.minecraftforge.installer;

import net.minecraftforge.installer.actions.ProgressCallback;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Ripped out of forge - modified to work for installer
 *
 * This class fixes older Java SSL setups which don't contain the correct root certificates to trust Let's Encrypt
 * https endpoints.
 *
 * It uses a secondary JKS keystore: lekeystore.jks, which contains the two root certificate keys as documented here:
 * <a href="https://letsencrypt.org/certificates/">https://letsencrypt.org/certificates/</a>
 *
 * To create the keystore, the following commands were run:
 * <pre>
 *     keytool -import -alias letsencryptisrgx1 -file isrgrootx1.pem -keystore lekeystore.jks -storetype jks -storepass supersecretpassword -v
 *     keytool -import -alias identrustx3 -file identrustx3.pem -keystore lekeystore.jks -storetype jks -storepass supersecretpassword -v
 * </pre>
 *
 * The PEM files were obtained from the above URL.
 */
class FixSSL {

    private static boolean hasJavaForDownload(ProgressCallback callback)
    {
        String javaVersion = System.getProperty("java.version");
        callback.message("Found java version " + javaVersion);
        if (javaVersion != null && javaVersion.startsWith("1.8.0_")) {
            try {
                if (Integer.parseInt(javaVersion.substring("1.8.0_".length())) < 101)
                    return false;
            } catch (NumberFormatException e) {
                e.printStackTrace();
                callback.message("Could not parse java version!");
            }
        }
        return true;
    }

    static void fixup(ProgressCallback callback) {
        if (hasJavaForDownload(callback)) return;
        try {
            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            Path ksPath = Paths.get(System.getProperty("java.home"),"lib", "security", "cacerts");
            keyStore.load(Files.newInputStream(ksPath), "changeit".toCharArray());
            final Map<String, Certificate> jdkTrustStore = Collections.list(keyStore.aliases()).stream().collect(Collectors.toMap(a -> a, (String alias) -> {
                try {
                    return keyStore.getCertificate(alias);
                } catch (KeyStoreException e) {
                    throw new UncheckedKeyStoreException(e);
                }
            }));

            final KeyStore leKS = KeyStore.getInstance(KeyStore.getDefaultType());
            final InputStream leKSFile = FixSSL.class.getResourceAsStream("/lekeystore.jks");
            leKS.load(leKSFile, "supersecretpassword".toCharArray());
            final Map<String, Certificate> leTrustStore = Collections.list(leKS.aliases()).stream().collect(Collectors.toMap(a -> a, (String alias) -> {
                try {
                    return leKS.getCertificate(alias);
                } catch (KeyStoreException e) {
                    throw new UncheckedKeyStoreException(e);
                }
            }));

            final KeyStore mergedTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            mergedTrustStore.load(null, new char[0]);
            for (Map.Entry<String, Certificate> entry : jdkTrustStore.entrySet()) {
                mergedTrustStore.setCertificateEntry(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String , Certificate> entry : leTrustStore.entrySet()) {
                mergedTrustStore.setCertificateEntry(entry.getKey(), entry.getValue());
            }

            final TrustManagerFactory instance = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            instance.init(mergedTrustStore);
            final SSLContext tls = SSLContext.getInstance("TLS");
            tls.init(null, instance.getTrustManagers(), null);
            HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());
            callback.message("Added Lets Encrypt root certificates as additional trust");
        } catch (UncheckedKeyStoreException | KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | KeyManagementException e) {
            callback.message("Failed to load lets encrypt certificate. Expect problems", ProgressCallback.MessagePriority.HIGH);
            e.printStackTrace();
        }
    }

    private static class UncheckedKeyStoreException extends RuntimeException {

        public UncheckedKeyStoreException(Throwable cause) {
            super(cause);
        }
    }
}
