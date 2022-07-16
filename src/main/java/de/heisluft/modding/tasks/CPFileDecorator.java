package de.heisluft.modding.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
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

  @Input
  public abstract SetProperty<File> getPaths();

  public CPFileDecorator() {
    getPaths().convention(new HashSet<>());
    getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("cp.txt")));
  }

  @TaskAction
  public void generate() throws IOException {
    Set<File> effectivePaths = new HashSet<>(getPaths().get());

    getProject().getConfigurations().getByName("runtimeClasspath").getResolvedConfiguration().getFiles().stream()
        .filter(f -> !f.getName().contains("-natives-"))
        .forEach(effectivePaths::add);

    Files.write(getOutput().get().getAsFile().toPath(), effectivePaths.stream().map(File::getAbsolutePath).collect(Collectors.toList()));
  }
}