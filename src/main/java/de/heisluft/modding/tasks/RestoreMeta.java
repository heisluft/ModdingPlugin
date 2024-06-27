package de.heisluft.modding.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class RestoreMeta extends OutputtingJavaExec {

  public RestoreMeta() {
    getMainClass().set("de.heisluft.deobf.tooling.binfix.BinFixer");
    setOutputFilename("minecraft-restored.jar");
  }

  @InputFile
  public abstract RegularFileProperty getInput();

  @OutputFile
  @Optional
  public abstract RegularFileProperty getMappings();

  public void setMappingsFileName(String filename) {
    getMappings().set(getProject().getLayout().getBuildDirectory().file(getName() + "/" + filename));
  }

  @Override
  @TaskAction
  public void exec() {
    args(
        getInput().getAsFile().get().getAbsolutePath(),
        getOutput().getAsFile().get().getAbsolutePath()
    );
    if(getMappings().isPresent()) args(getMappings().getAsFile().get().getAbsolutePath());
    super.exec();
  }
}
