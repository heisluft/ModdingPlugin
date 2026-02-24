package de.heisluft.modding.util;

import java.util.Objects;

public final class ArtifactIdentifier {
  public final String groupId;
  public final String artifactId;
  public final String version;
  public final String classifier;
  public final String extension;

  public ArtifactIdentifier(String groupId, String artifactId, String version, String classifier, String extension) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.classifier = classifier;
    this.extension = extension;
  }

  @Override
  public boolean equals(Object o) {
    if(!(o instanceof ArtifactIdentifier)) return false;
    ArtifactIdentifier that = (ArtifactIdentifier) o;
    return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) &&
        Objects.equals(version, that.version) && Objects.equals(classifier, that.classifier) &&
        Objects.equals(extension, that.extension);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, artifactId, version, classifier, extension);
  }
}
