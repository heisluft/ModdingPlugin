package de.heisluft.modding.repo;

import de.heisluft.modding.tasks.Zip2ZipCopy;
import de.heisluft.modding.util.Util;
import net.minecraftforge.artifactural.api.artifact.Artifact;
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.artifactural.api.artifact.ArtifactType;
import net.minecraftforge.artifactural.api.repository.ArtifactProvider;
import net.minecraftforge.artifactural.api.repository.Repository;
import net.minecraftforge.artifactural.base.artifact.StreamableArtifact;
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder;
import net.minecraftforge.artifactural.base.repository.SimpleRepository;
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.invocation.Gradle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

public class ResourceRepo implements ArtifactProvider<ArtifactIdentifier> {

  private final File cacheRoot;
  private final Repository repo;
  private static ResourceRepo instance;

  private static ResourceRepo getInstance(Gradle gradle) {
    if(instance == null) instance = new ResourceRepo(gradle);
    return instance;
  }

  private ResourceRepo(Gradle gradle) {
    cacheRoot = Util.getCache(gradle, "resource_repo").toFile();
    repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class).provide(this));

  }

  public static void init(Project project) {
    ResourceRepo repo = getInstance(project.getGradle());
    GradleRepositoryAdapter gra = GradleRepositoryAdapter.add(project.getRepositories(), "DYNAMIC_asset_repo", instance.cacheRoot, repo.repo);
    // disable pom resolution
    try {
      Field f = GradleRepositoryAdapter.class.getDeclaredField("local");
      f.setAccessible(true);
      ((MavenArtifactRepository) f.get(gra)).metadataSources(MavenArtifactRepository.MetadataSources::artifact);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e); // We don't want to do faulty builds so we just crash
    }
  }

  @Override
  public Artifact getArtifact(ArtifactIdentifier info) {
    // We only handle mojangs minecraft assets
    if(!info.getGroup().equals("com.mojang") || !info.getName().equals("minecraft-assets")) return null;
    File result = new File(cacheRoot, "minecraft-assets-" + info.getVersion() + ".jar");
    if(result.isFile()) return StreamableArtifact.ofFile(info, ArtifactType.OTHER, result);
    try {
      Zip2ZipCopy.doExec(MCRepo.getInstance().resolve("minecraft", info.getVersion()).toFile(), result, Arrays.asList("**.png" ,"**.gif"));
    } catch(IOException e) {
      return null;
    }
    return StreamableArtifact.ofFile(info, ArtifactType.OTHER, result);
  }
}
