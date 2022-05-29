package de.heisluft.modding.repo;

import de.heisluft.modding.ClassicModdingPlugin;
import de.heisluft.modding.tasks.MavenDownload;
import de.heisluft.modding.tasks.Zip2ZipCopy;
import de.heisluft.modding.util.IdentifierUtil;
import de.heisluft.modding.util.Util;
import net.minecraftforge.artifactural.api.artifact.Artifact;
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.artifactural.api.artifact.ArtifactType;
import net.minecraftforge.artifactural.api.repository.ArtifactProvider;
import net.minecraftforge.artifactural.api.repository.Repository;
import net.minecraftforge.artifactural.base.artifact.SimpleArtifactIdentifier;
import net.minecraftforge.artifactural.base.artifact.StreamableArtifact;
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder;
import net.minecraftforge.artifactural.base.repository.SimpleRepository;
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ResourceRepo implements ArtifactProvider<ArtifactIdentifier> {

  private final File cacheRoot;
  private final Repository repo;
  private static ResourceRepo instance;
  private final Project project;

  public byte[] genPom(ArtifactIdentifier info) {
    return (
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 " +
            "https://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
            "<modelVersion>4.0.0</modelVersion><groupId>" + info.getGroup() + "</groupId>" +
            "<artifactId>" + info.getName() + "</artifactId><version>" + info.getVersion() + "</version></project>"
    ).getBytes(StandardCharsets.UTF_8);
  }

  private static ResourceRepo getInstance(Project project) {
    if(instance == null) instance = new ResourceRepo(project);
    return instance;
  }

  private ResourceRepo(Project project) {
    this.project = project;
    cacheRoot = Util.getCache(project, "resource_repo");
    repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class).provide(this));

  }

  public static void init(Project project) {
    ResourceRepo repo = getInstance(project);
    GradleRepositoryAdapter.add(project.getRepositories(), "DYNAMIC_asset_repo", instance.cacheRoot, repo.repo);
  }

  @Override
  public Artifact getArtifact(ArtifactIdentifier info) {
    String v = info.getVersion();
    if(!info.getGroup().equals("com.mojang") || !info.getName().equals("minecraft-assets")) return null;
    if(info.getExtension().equals("pom")) {
      File target = new File(cacheRoot, "com/mojang/minecraft-assets/" + v + "/minecraft-assets-" + v + ".pom");
      if(target.isFile()) return StreamableArtifact.ofFile(info, ArtifactType.OTHER, target);
      try {
        Path p = target.toPath();
        Files.createDirectories(p.getParent());
        Files.write(p, genPom(info));
      } catch(IOException e) {
        throw new UncheckedIOException(e);
      }
      return StreamableArtifact.ofFile(info, ArtifactType.OTHER, target);
    }
    File dest = new File(cacheRoot, info.getVersion() + ".jar");
    File result = new File(cacheRoot, info.getVersion() + "-assets.jar");
    if(result.isFile())
      return StreamableArtifact.ofFile(info, ArtifactType.OTHER, result);
    try {
      MavenDownload.manualDownload(ClassicModdingPlugin.REPO_URL, IdentifierUtil.withName(info, "minecraft"), dest);
    } catch(IOException e) {
      e.printStackTrace();
      return null;
    }
    try {
      Zip2ZipCopy.doExec(dest, result, Arrays.asList("**.png" ,"**.gif"));
    } catch(IOException e) {
      return null;
    }
    return StreamableArtifact.ofFile(info, ArtifactType.OTHER, result);
  }
}
