package de.heisluft.modding.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class CPFileDecorator extends DefaultTask {
  @OutputFile
  public abstract RegularFileProperty getOutput();

  @OutputFile
  public abstract RegularFileProperty getGameCPFile();

  @Input
  public abstract SetProperty<File> getPaths();

  @InputFile
  public abstract RegularFileProperty getMinecraftJarPath();

  public CPFileDecorator() {
    getPaths().convention(new HashSet<>());
    getOutput().set(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("cp.txt")));
    getGameCPFile().set(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("gamecp.txt")));
  }

  @TaskAction
  public void generate() throws IOException {
    Set<File> bootStrapCP = new HashSet<>(getPaths().get());
    ConfigurationContainer cfg = getProject().getConfigurations();
    cfg.getByName("runtimeClasspath").getResolvedConfiguration().getResolvedArtifacts().stream()
        .map(ResolvedArtifact::getFile)
        .forEach(bootStrapCP::add);
    Set<File> gameCP = new HashSet<>();
    cfg.getByName("mcRuntimeClasspath").getResolvedConfiguration().getResolvedArtifacts().stream()
        .map(ResolvedArtifact::getFile)
        .forEach(gameCP::add);
    bootStrapCP.removeAll(gameCP);
    gameCP.add(getMinecraftJarPath().get().getAsFile());
    Files.write(getOutput().get().getAsFile().toPath(), bootStrapCP.stream().map(File::getAbsolutePath).collect(Collectors.toList()));
    Files.write(getGameCPFile().get().getAsFile().toPath(), gameCP.stream().map(File::getAbsolutePath).collect(Collectors.toList()));
  }
}