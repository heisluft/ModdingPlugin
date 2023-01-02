package de.heisluft.modding.tasks;

import de.heisluft.modding.repo.MCRepo;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;

public abstract class FetchMCTask extends DefaultTask {

  @OutputFile
  public abstract RegularFileProperty getOutput();
  @Input
  public abstract Property<String> getMCVersion();

  public FetchMCTask() {
    getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("minecraft.jar")));
  }

  @TaskAction
  public void run() throws IOException {
    Files.copy(MCRepo.getInstance().resolve("minecraft", getMCVersion().get()), getOutput().getAsFile().get().toPath());
  }
}
