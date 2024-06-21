package de.heisluft.modding.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class OutputtingJavaExec extends JavaExec {

  @OutputFile
  public abstract RegularFileProperty getOutput();

  public void setOutputFilename(String filename) {
    getOutput().set(getProject().getLayout().getBuildDirectory().file(getName() + "/" + filename));
  }

  @TaskAction
  @Override
  public void exec() {
    super.exec();
  }
}
