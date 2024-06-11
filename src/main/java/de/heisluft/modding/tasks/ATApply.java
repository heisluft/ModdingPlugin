package de.heisluft.modding.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

public abstract class ATApply extends OutputtingJavaExec {

  public ATApply() {
    onlyIf(t -> {
      System.out.println("called");
      return this.getATFile().isPresent() && this.getATFile().get().getAsFile().exists();
    });
    getMainClass().set("de.heisluft.reveng.at.ATApplicator");
    setOutputFilename("minecraft-at.jar");
  }

  @InputFile
  public abstract RegularFileProperty getInput();

  @InputFile
  @Optional
  public abstract RegularFileProperty getATFile();

  @TaskAction
  @Override
  public void exec() {
    args(
        getInput().get().getAsFile().getAbsolutePath(),
        getATFile().get().getAsFile().getAbsolutePath(),
        getOutput().get().getAsFile().getAbsolutePath());
    super.exec();
  }
}
