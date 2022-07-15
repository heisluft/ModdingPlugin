package de.heisluft.modding.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.stream.Collectors;

public abstract class CPFileDecorator extends DefaultTask {
  @OutputFile
  public abstract RegularFileProperty getOutput();

  public CPFileDecorator() {
    getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("cp.txt")));
  }

  @TaskAction
  public void generate() throws IOException {
    Files.write(
        getOutput().getAsFile().get().toPath(),
        getProject().getConfigurations().getByName("runtimeClasspath").getResolvedConfiguration().getFiles().stream()
            .filter(f -> !f.getName().contains("-natives-"))
            .map(File::getAbsolutePath).map(s -> s + "\n")
            .collect(Collectors.joining())
            .getBytes(StandardCharsets.UTF_8)
    );
  }
}