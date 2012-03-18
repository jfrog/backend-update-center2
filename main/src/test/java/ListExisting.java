import org.jenkins_ci.update_center.DefaultMavenRepositoryBuilder;
import org.jenkins_ci.update_center.model.HPI;
import org.jenkins_ci.update_center.model.PluginHistory;
import org.jenkins_ci.update_center.repo.MavenRepository;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * List up existing groupIds used by plugins.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListExisting {
    public static void main(String[] args) throws Exception {
        MavenRepository r = DefaultMavenRepositoryBuilder.createStandardInstance();

        Set<String> groupIds = new TreeSet<String>();
        Collection<PluginHistory> all = r.listHudsonPlugins();
        for (PluginHistory p : all) {
            HPI hpi = p.latest();
            groupIds.add(hpi.artifact.groupId);
        }

        for (String groupId : groupIds) {
            System.out.println(groupId);
        }
    }
}
