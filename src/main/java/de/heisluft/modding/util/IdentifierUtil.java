package de.heisluft.modding.util;

import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.artifactural.base.artifact.SimpleArtifactIdentifier;

public class IdentifierUtil {

  public static ArtifactIdentifier withName(ArtifactIdentifier source, String name) {
    return new SimpleArtifactIdentifier(source.getGroup(), name, source.getVersion(), source.getClassifier(), source.getExtension());
  }

  public static ArtifactIdentifier parseIdentifier(String artifactIdentifier) {
    //Dont ever resize this, we want to be fast and ram is not a concern for strings crafted in good faith
    StringBuilder groupBuilder = new StringBuilder(artifactIdentifier.length());
    StringBuilder artifactNameBuilder = new StringBuilder(artifactIdentifier.length());
    StringBuilder versionBuilder = new StringBuilder(artifactIdentifier.length());
    StringBuilder classifierBuilder = new StringBuilder(artifactIdentifier.length());
    StringBuilder extensionBuilder = new StringBuilder(artifactIdentifier.length());
    boolean hasExt = false, hasClassifier = false, hasVersion = false;
    StringBuilder currentBuilder = groupBuilder;
    for (char c : artifactIdentifier.toCharArray()) {
      switch (c) {
        case ':':
          if(currentBuilder == groupBuilder) {
            if(groupBuilder.length() == 0) throw new IllegalArgumentException("GroupId must not be empty");
            currentBuilder = artifactNameBuilder;
          } else if(currentBuilder == artifactNameBuilder) {
            if(artifactNameBuilder.length() == 0) throw new IllegalArgumentException("ArtifactName must not be empty");
            currentBuilder = versionBuilder;
            hasVersion = true;
          } else if(currentBuilder == versionBuilder) {
            if(versionBuilder.length() == 0) throw new IllegalArgumentException("Version must not be empty if specified");
            currentBuilder = classifierBuilder;
            hasClassifier = true;
          } else throw new IllegalArgumentException("Illegal char ':' in classifier or extension");
          break;
        case '@':
          if(currentBuilder == versionBuilder || currentBuilder == classifierBuilder) {
            if(currentBuilder == versionBuilder && versionBuilder.length() == 0)
              throw new IllegalArgumentException("Version must not be empty if specified");
            if(currentBuilder == classifierBuilder && classifierBuilder.length() == 0)
              throw new IllegalArgumentException("Classifier must not be empty if specified");
            hasExt = true;
            currentBuilder = extensionBuilder;
            break;
          }
        default:
          currentBuilder.append(c);

      }
    }
    if(groupBuilder.length() == 0) throw new IllegalArgumentException("GroupId must not be empty");
    if(artifactNameBuilder.length() == 0) throw new IllegalArgumentException("ArtifactName must not be empty");
    if(hasVersion && versionBuilder.length() == 0) throw new IllegalArgumentException("Version must not be empty if specified");
    if(hasClassifier && classifierBuilder.length() == 0) throw new IllegalArgumentException("Classifier must not be empty if specified");
    String group = groupBuilder.toString();
    String name = artifactNameBuilder.toString();
    String version = hasVersion ? versionBuilder.toString() : "latest";
    String classifier = hasClassifier ? classifierBuilder.toString() : null;
    String extension = hasExt ? extensionBuilder.toString() : "jar";
    return new SimpleArtifactIdentifier(group, name, version, classifier, extension);
  }
}
