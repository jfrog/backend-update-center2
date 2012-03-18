package org.jenkins_ci.update_center.repo;

import hudson.util.VersionNumber;
import org.jenkins_ci.update_center.model.GenericArtifactInfo;
import org.jenkins_ci.update_center.model.HPI;
import org.jenkins_ci.update_center.model.HudsonWar;
import org.jenkins_ci.update_center.model.PluginHistory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Delegating {@link MavenRepository} to limit the data to the subset compatible with the specific version.
 *
 * @author Kohsuke Kawaguchi
 */
public class VersionCappedMavenRepository extends MavenRepository {
    private final MavenRepository base;

    /**
     * Version number to cap. We only report the portion of data that's compatible with this version.
     */
    private final VersionNumber cap;

    public VersionCappedMavenRepository(MavenRepository base, VersionNumber cap) {
        this.base = base;
        this.cap = cap;
    }

    @Override
    public TreeMap<VersionNumber, HudsonWar> getHudsonWar() throws IOException {
        return new TreeMap<VersionNumber, HudsonWar>(base.getHudsonWar().tailMap(cap, true));
    }

    @Override
    public Collection<PluginHistory> listHudsonPlugins() throws IOException {
        Collection<PluginHistory> r = base.listHudsonPlugins();
        for (Iterator<PluginHistory> jtr = r.iterator(); jtr.hasNext(); ) {
            PluginHistory h = jtr.next();

            for (Iterator<Entry<VersionNumber, HPI>> itr = h.artifacts.entrySet().iterator(); itr.hasNext(); ) {
                Entry<VersionNumber, HPI> e = itr.next();
                try {
                    VersionNumber v = new VersionNumber(e.getValue().getRequiredJenkinsVersion());
                    if (v.compareTo(cap) <= 0) {
                        continue;
                    }
                } catch (IOException x) {
                    x.printStackTrace();
                }
                itr.remove();
            }

            if (h.artifacts.isEmpty()) {
                jtr.remove();
            }
        }

        return r;
    }


    @Override
    public File resolve(GenericArtifactInfo a, String type, String classifier) throws IOException {
        return base.resolve(a, type, classifier);
    }

    @Override
    public void addRemoteRepository(String id, File indexDirectory, URL repository) throws Exception {
        base.addRemoteRepository(id, indexDirectory, repository);
    }

    @Override
    public void addRemoteRepository(String id, URL remoteIndex, URL repository) throws Exception {
        base.addRemoteRepository(id, remoteIndex, repository);
    }
}
