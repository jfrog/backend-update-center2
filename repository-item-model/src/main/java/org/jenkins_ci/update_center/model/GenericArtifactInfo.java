package org.jenkins_ci.update_center.model;

/**
 * @author Noam Y. Tenne
 */
public class GenericArtifactInfo {

    public final String repository;
    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String classifier;
    public final String packaging;

    public GenericArtifactInfo(String repository, String groupId, String artifactId, String version, String classifier,
            String packaging) {
        this.repository = repository;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.packaging = packaging;
    }

    public GenericArtifactInfo(String repository, String groupId, String artifactId, String version,
            String classifier) {
        this(repository, groupId, artifactId, version, classifier, null);
    }
}
