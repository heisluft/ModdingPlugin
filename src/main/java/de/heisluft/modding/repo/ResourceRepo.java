package de.heisluft.modding.repo;

import de.heisluft.modding.util.Util;
import net.minecraftforge.artifactural.api.artifact.Artifact;
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.artifactural.api.repository.ArtifactProvider;
import net.minecraftforge.artifactural.api.repository.Repository;
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder;
import net.minecraftforge.artifactural.base.repository.SimpleRepository;
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter;
import org.gradle.api.Project;

import java.io.File;

public class ResourceRepo implements ArtifactProvider<ArtifactIdentifier> {

  private final File cacheRoot;
  private final Repository repo;
  private static ResourceRepo instance;

  private static ResourceRepo getInstance(Project project) {
    if(instance == null) instance = new ResourceRepo(project);
    return instance;
  }

  private ResourceRepo(Project project) {
    cacheRoot = Util.getCache(project, "resource_repo");
    repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class).provide(this));
  }

  public static void init(Project project) {
    ResourceRepo repo = getInstance(project);
    GradleRepositoryAdapter.add(project.getRepositories(), "DYNAMIC_asset_repo", instance.cacheRoot, repo.repo);
  }

  @Override
  public Artifact getArtifact(ArtifactIdentifier info) {
    System.out.println("trying to resolve: " + info);
    return null;
  }
}
