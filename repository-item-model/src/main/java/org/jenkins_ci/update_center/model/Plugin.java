/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc.
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

import hudson.plugins.jira.soap.RemotePage;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An entry of a plugin in the update center metadata.
 *
 * @author Kohsuke Kawaguchi
 */
public class Plugin {
    /**
     * Plugin artifact ID.
     */
    public final String artifactId;
    /**
     * Latest version of this plugin.
     */
    public final HPI latest;
    /**
     * Latest version of this plugin.
     */
    public final HPI previous;
    /**
     * Confluence page of this plugin in Wiki. Null if we couldn't find it.
     */
    public final RemotePage page;

    /**
     * Confluence labels for the plugin wiki page. Null if wiki page wasn't found.
     */
    private String[] labels;

    /**
     * Deprecated plugins should not be included in update center.
     */
    private boolean deprecated = false;

    /**
     * POM parsed as a DOM.
     */
    private final Document pom;
    private final Document parentPom;

    public Plugin(String artifactId, HPI latest, HPI previous, Document pom, Document parentPom, RemotePage page,
            String[] labels) throws IOException {
        this.artifactId = artifactId;
        this.latest = latest;
        this.previous = previous;
        this.pom = pom;
        this.parentPom = parentPom;
        this.page = page;
        this.labels = Arrays.copyOf(labels, labels.length);
        markDeprecated();
    }

    public Plugin(PluginHistory hpi, Document pom, Document parentPom, RemotePage page, String[] labels)
            throws IOException {
        this.artifactId = hpi.artifactId;
        List<HPI> versions = new ArrayList<HPI>(hpi.artifacts.values());
        this.latest = versions.get(0);
        this.previous = versions.size() > 1 ? versions.get(1) : null;

        // Doublecheck that latest-by-version is also latest-by-date:
        checkLatestDate(versions, latest);

        this.pom = pom;
        this.parentPom = parentPom;
        this.page = page;
        this.labels = Arrays.copyOf(labels, labels.length);
        markDeprecated();
    }

    public Plugin(HPI hpi, Document pom, Document parentPom, RemotePage page, String[] labels) throws IOException {
        this(hpi.artifact.artifactId, hpi, null, pom, parentPom, page, labels);
    }

    private static void checkLatestDate(Collection<HPI> artifacts, HPI latestByVersion) throws IOException {
        TreeMap<Long, HPI> artifactsByDate = new TreeMap<Long, HPI>();
        for (HPI h : artifacts) {
            artifactsByDate.put(h.getTimestamp(), h);
        }
        HPI latestByDate = artifactsByDate.get(artifactsByDate.lastKey());
        if (latestByDate != latestByVersion) {
            System.out.println(
                    "** Latest-by-version (" + latestByVersion.version + ','
                            + latestByVersion.getTimestampAsString() + ") doesn't match latest-by-date ("
                            + latestByDate.version + ',' + latestByDate.getTimestampAsString() + ')');
        }
    }


    private static Node selectSingleNode(Document pom, String path) {
        Node result = pom.selectSingleNode(path);
        if (result == null) {
            result = pom.selectSingleNode(path.replaceAll("/", "/m:"));
        }
        return result;
    }

    private static String selectSingleValue(Document dom, String path) {
        Node node = selectSingleNode(dom, path);
        return node != null ? ((Element) node).getTextTrim() : null;
    }

    private static final Pattern HOSTNAME_PATTERN =
            Pattern.compile("(?:://|scm:git:(?!\\w+://))(?:\\w*@)?([\\w.-]+)[/:]");

    /**
     * Get hostname of SCM specified in POM of latest release, or null. Used to determine if source lives in github or
     * svn.
     */
    public String getScmHost() {
        if (pom != null) {
            String scm = selectSingleValue(pom, "/project/scm/connection");
            if (scm == null) {
                if (parentPom != null) {
                    scm = selectSingleValue(parentPom, "/project/scm/connection");
                }
            }
            if (scm != null) {
                Matcher m = HOSTNAME_PATTERN.matcher(scm);
                if (m.find()) {
                    return m.group(1);
                } else {
                    System.out.println("** Unable to parse scm/connection: " + scm);
                }
            } else {
                System.out.println("** No scm/connection found in pom");
            }
        }
        return null;
    }

    public String[] getLabels() {
        return Arrays.copyOf(labels, labels.length);
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    private void markDeprecated() {
        if (labels != null) {
            for (String label : labels) {
                if ("deprecated".equals(label)) {
                    deprecated = true;
                    break;
                }
            }
        }
    }

    /**
     * Obtains the excerpt of this wiki page in HTML. Otherwise null.
     */
    public String getExcerptInHTML() {
        String content = page.getContent();
        if (content == null) {
            return null;
        }

        Matcher m = EXCERPT_PATTERN.matcher(content);
        if (!m.find()) {
            return null;
        }

        String excerpt = m.group(1);
        String oneLiner = NEWLINE_PATTERN.matcher(excerpt).replaceAll(" ");
        return HYPERLINK_PATTERN.matcher(oneLiner).replaceAll("<a href='$2'>$1</a>");
    }

    // Tweaking to ignore leading whitespace after the initial {excerpt}
    private static final Pattern EXCERPT_PATTERN =
            Pattern.compile("\\{excerpt(?::hidden(?:=true)?)?\\}\\s*(.+)\\{excerpt\\}", Pattern.DOTALL);
    private static final Pattern HYPERLINK_PATTERN = Pattern.compile("\\[([^|\\]]+)\\|([^|\\]]+)(|([^]])+)?\\]");
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("(?:\\r\\n|\\n)");

    public String getTitle() {
        String title = page != null ? page.getTitle() : null;
        if (title == null) {
            title = selectSingleValue(pom, "/project/name");
        }
        if (title == null) {
            title = artifactId;
        }
        return title;
    }

    public String getWiki() {
        String wiki = "";
        if (page != null) {
            wiki = page.getUrl();
        }
        return wiki;
    }

    public JSONObject toJSON() throws IOException {
        JSONObject json = latest.toJSON(artifactId);

        SimpleDateFormat fisheyeDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'.00Z'", Locale.US);
        fisheyeDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        json.put("releaseTimestamp", fisheyeDateFormatter.format(latest.getTimestamp()));
        if (previous != null) {
            json.put("previousVersion", previous.version);
            json.put("previousTimestamp", fisheyeDateFormatter.format(previous.getTimestamp()));
        }

        json.put("title", getTitle());
        if (page != null) {
            json.put("wiki", page.getUrl());
            String excerpt = getExcerptInHTML();
            if (excerpt != null) {
                json.put("excerpt", excerpt);
            }
            String[] labelList = getLabels();
            if (labelList != null) {
                json.put("labels", labelList);
            }
        }
        String scm = getScmHost();
        if (scm != null) {
            json.put("scm", scm);
        }

        if (!json.has("excerpt")) {
            // fall back to <description>, which is plain text but still better than nothing.
            String description = plainText2html(selectSingleValue(pom, "/project/description"));
            if (description != null) {
                json.put("excerpt", description);
            }
        }

        HPI hpi = latest;
        json.put("requiredCore", hpi.getRequiredJenkinsVersion());

        if (hpi.getCompatibleSinceVersion() != null) {
            json.put("compatibleSinceVersion", hpi.getCompatibleSinceVersion());
        }
        if (hpi.getSandboxStatus() != null) {
            json.put("sandboxStatus", hpi.getSandboxStatus());
        }

        JSONArray deps = new JSONArray();
        for (HPI.Dependency d : hpi.getDependencies()) {
            deps.add(d.toJSON());
        }
        json.put("dependencies", deps);

        JSONArray devs = new JSONArray();
        List<HPI.Developer> devList = hpi.getDevelopers();
        if (!devList.isEmpty()) {
            for (HPI.Developer dev : devList) {
                devs.add(dev.toJSON());
            }
        } else {
            String builtBy = latest.getBuiltBy();
            if (builtBy != null) {
                devs.add(new HPI.Developer("", builtBy, "").toJSON());
            }
        }
        json.put("developers", devs);

        return json;
    }

    private String plainText2html(String plainText) {
        if (plainText == null || plainText.length() == 0) {
            return "";
        }
        return plainText.replace("&", "&amp;").replace("<", "&lt;");
    }

    public static final Properties OVERRIDES = new Properties();

    static {
        try {
            OVERRIDES.load(Plugin.class.getClassLoader().getResourceAsStream("wiki-overrides.properties"));
        } catch (IOException e) {
            throw new Error(e);
        }
    }
}
