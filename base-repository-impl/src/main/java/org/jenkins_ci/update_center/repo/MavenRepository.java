package org.jenkins_ci.update_center.repo;

import hudson.util.VersionNumber;
import org.jenkins_ci.update_center.model.GenericArtifactInfo;
import org.jenkins_ci.update_center.model.HPI;
import org.jenkins_ci.update_center.model.HudsonWar;
import org.jenkins_ci.update_center.model.MavenArtifact;
import org.jenkins_ci.update_center.model.PluginHistory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * A collection of artifacts from which we build index.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class MavenRepository {

    protected static final Properties IGNORE = new Properties();

    protected Integer maxPlugins;
    protected String username;
    protected String password;

    /**
     * Discover all plugins from this Maven repository.
     */
    public Collection<PluginHistory> listHudsonPlugins() throws IOException {
        Map<String, PluginHistory> plugins = new TreeMap<String, PluginHistory>(String.CASE_INSENSITIVE_ORDER);
        listHudsonPlugins(plugins);
        return reduceToMaxPluginsIfSpecified(plugins.values());
    }

    /**
     * Discover all plugins from this Maven repository in order released, not using PluginHistory.
     */
    public Map<Date, Map<String, HPI>> listHudsonPluginsByReleaseDate() throws IOException {
        Collection<PluginHistory> all = listHudsonPlugins();

        Map<Date, Map<String, HPI>> plugins = new TreeMap<Date, Map<String, HPI>>();

        for (PluginHistory p : all) {
            for (HPI h : p.artifacts.values()) {
                h.file = resolve(h.artifact);
                try {
                    Date releaseDate = h.getTimestampAsDate();
                    System.out.println("adding " + h.artifact.artifactId + ":" + h.version);
                    Map<String, HPI> pluginsOnDate = plugins.get(releaseDate);
                    if (pluginsOnDate == null) {
                        pluginsOnDate = new TreeMap<String, HPI>();
                        plugins.put(releaseDate, pluginsOnDate);
                    }
                    pluginsOnDate.put(p.artifactId, h);
                } catch (IOException e) {
                    // if we fail to resolve artifact, move on
                    e.printStackTrace();
                }
            }
        }

        return plugins;
    }

    /**
     * find the HPI for the specified plugin
     *
     * @return the found HPI or null
     */
    public HPI findPlugin(String groupId, String artifactId, String version) throws IOException {
        Collection<PluginHistory> all = listHudsonPlugins();

        for (PluginHistory p : all) {
            for (HPI h : p.artifacts.values()) {
                if (h.isEqualsTo(groupId, artifactId, version)) {
                    return h;
                }
            }
        }
        return null;
    }


    public TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException {
        TreeMap<VersionNumber, HudsonWar> r = new TreeMap<VersionNumber, HudsonWar>(VersionNumber.DESCENDING);
        listWar(r, "org.jenkins-ci.main", null);
        listWar(r, "org.jvnet.hudson.main", MavenArtifact.CUT_OFF);
        return r;
    }

    public File resolve(GenericArtifactInfo a) throws IOException {
        return resolve(a, a.packaging, a.classifier);
    }

    public abstract File resolve(GenericArtifactInfo a, String type, String classifier) throws IOException;

    public void setMaxPlugins(Integer maxPlugins) {
        this.maxPlugins = maxPlugins;
    }

    public File resolvePOM(GenericArtifactInfo artifact) throws IOException {
        return resolve(artifact, "pom", null);
    }

    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    protected abstract void listWar(TreeMap<VersionNumber, HudsonWar> r, String groupId, VersionNumber cap)
            throws IOException;

    protected abstract void listHudsonPlugins(Map<String, PluginHistory> plugins) throws IOException;

    protected boolean isWarValid(HudsonWar warInfo, VersionNumber cap) {
        if (warInfo.version.contains("SNAPSHOT")) {
            return false;
        }
        if (!warInfo.artifact.artifactId.equals("jenkins-war")
                && !warInfo.artifact.artifactId.equals("hudson-war")) {
            return false;
        }
        if (warInfo.artifact.classifier != null) {
            return false;
        }
        if (cap != null && new VersionNumber(warInfo.version).compareTo(cap) > 0) {
            return false;
        }
        return true;
    }

    protected boolean isHpiValid(HPI hpiInfo) {
        if (hpiInfo.version.contains("SNAPSHOT")) {
            return false;
        }
        if (IGNORE.containsKey(hpiInfo.artifact.artifactId) ||
                IGNORE.containsKey(hpiInfo.artifact.artifactId + "-" + hpiInfo.artifact.version)) {
            return false;
        }
        return true;
    }

    private Collection<PluginHistory> reduceToMaxPluginsIfSpecified(Collection<PluginHistory> values) {
        if (maxPlugins == null) {
            return values;
        }
        System.out.println("Limiting the number of plugins handled to " + maxPlugins);
        List<PluginHistory> result = new ArrayList<PluginHistory>(values);
        return result.subList(0, maxPlugins);
    }

    static {
        try {
            IGNORE.load(MavenRepository.class.getClassLoader().getResourceAsStream("artifact-ignores.properties"));
        } catch (IOException e) {
            throw new Error(e);
        }
    }
}
