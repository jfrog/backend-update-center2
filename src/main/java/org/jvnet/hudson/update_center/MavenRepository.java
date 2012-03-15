package org.jvnet.hudson.update_center;

import hudson.util.VersionNumber;
import org.jvnet.hudson.update_center.artifact.GenericArtifactInfo;
import org.jvnet.hudson.update_center.artifact.HPI;
import org.jvnet.hudson.update_center.artifact.HudsonWar;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

/**
 * A collection of artifacts from which we build index.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class MavenRepository {
    /**
     * Discover all plugins from this Maven repository.
     */
    public abstract Collection<PluginHistory> listHudsonPlugins() throws IOException;

    /**
     * Discover all plugins from this Maven repository in order released, not using PluginHistory.
     */
    public Map<Date, Map<String, HPI>> listHudsonPluginsByReleaseDate() throws IOException {
        Collection<PluginHistory> all = listHudsonPlugins();

        Map<Date, Map<String, HPI>> plugins = new TreeMap<Date, Map<String, HPI>>();

        for (PluginHistory p : all) {
            for (HPI h : p.artifacts.values()) {
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


    /**
     * Discover all hudson.war versions.
     */
    public abstract TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException;

    public File resolve(GenericArtifactInfo a) throws IOException {
        return resolve(a, a.packaging, null);
    }

    public abstract File resolve(GenericArtifactInfo a, String type, String classifier) throws IOException;
}
