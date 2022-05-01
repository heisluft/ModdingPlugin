package de.heisluft.modding;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public abstract class OutputtingJavaExec extends JavaExec {

  @OutputFile
  public abstract RegularFileProperty getOutput();

  public void setOutputFilename(String filename) {
    getOutput().set(new File(getProject().getBuildDir(), getName() + "/" + filename));
  }

  @TaskAction
  @Override
  public void exec() {
    super.exec();
  }
}
