package de.heisluft.modding.tasks;


import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class RemapTask extends OutputtingJavaExec {

  public RemapTask() {
    getMainClass().set("de.heisluft.deobf.tooling.Remapper");
  }

  @InputFile
  public abstract RegularFileProperty getInput();

  @InputFile
  public abstract RegularFileProperty getMappings();

  @TaskAction
  @Override
  public void exec() {
    args(
        "remap",
        getInput().get().getAsFile().getAbsolutePath(),
        getMappings().get().getAsFile().getAbsolutePath(),
        "-o",
        getOutput().get().getAsFile().getAbsolutePath());
    super.exec();
  }
}
