/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkins_ci.update_center.model;

import hudson.util.VersionNumber;
import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Artifact from a Maven repository and its metadata.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenArtifact {

    /**
     * Hudson -> Jenkins cut-over version.
     */
    public static final VersionNumber CUT_OFF = new VersionNumber("1.395");

    public final GenericArtifactInfo artifact;
    public final String version;
    public File file;

    // lazily computed
    private long timestamp;
    private Manifest manifest;

    public MavenArtifact(GenericArtifactInfo artifact) {
        this.artifact = artifact;
        version = artifact.version;
    }

    /**
     * Computes the SHA1 signature of the file.
     */
    public String getDigest() throws IOException {
        FileInputStream fin = null;
        try {
            MessageDigest sig = MessageDigest.getInstance("SHA1");
            fin = new FileInputStream(file);
            byte[] buf = new byte[2048];
            int len;
            while ((len = fin.read(buf, 0, buf.length)) >= 0) {
                sig.update(buf, 0, len);
            }

            return new String(Base64.encodeBase64(sig.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        } finally {
            IOUtils.closeQuietly(fin);
        }
    }

    public JSONObject toJSON(String name) throws IOException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("version", version);

        o.put("url", getURL().toExternalForm());
        o.put("buildDate", getTimestampAsString());
        o.put("sha1", getDigest());

        return o;
    }

    public VersionNumber getVersion() {
        return new VersionNumber(version);
    }

    public String getTimestampAsString() throws IOException {
        long lastModified = getTimestamp();
        SimpleDateFormat bdf = getDateFormat();

        return bdf.format(lastModified);
    }

    public Date getTimestampAsDate() throws IOException {
        long lastModified = getTimestamp();
        SimpleDateFormat bdf = getDateFormat();

        Date tsDate;

        try {
            tsDate = bdf.parse(bdf.format(new Date(lastModified)));
        } catch (ParseException pe) {
            throw new IOException(pe.getMessage());
        }

        return tsDate;
    }

    public static SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    }

    public long getTimestamp() throws IOException {
        if (timestamp == 0) {
            getManifest();
        }
        return timestamp;
    }

    public Manifest getManifest() throws IOException {
        if (manifest == null) {
            try {
                JarFile jar = new JarFile(file);
                ZipEntry e = jar.getEntry("META-INF/MANIFEST.MF");
                timestamp = e.getTime();
                manifest = jar.getManifest();
                jar.close();
            } catch (IOException x) {
                throw (IOException) new IOException("Failed to open " + file).initCause(x);
            }
        }
        return manifest;
    }

    public Attributes getManifestAttributes() throws IOException {
        return getManifest().getMainAttributes();
    }

    /**
     * Where to download from?
     */
    public URL getURL() throws MalformedURLException {
        return new URL(
                "repo.jenkins-ci.org/public/" + artifact.groupId.replace('.', '/') + "/" + artifact.artifactId + "/" +
                        artifact.version + "/" + artifact.artifactId + "-" + artifact.version + "." +
                        artifact.packaging);
    }

    @Override
    public String toString() {
        return artifact.toString();
    }
}
