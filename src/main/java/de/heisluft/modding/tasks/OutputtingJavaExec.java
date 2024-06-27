package de.heisluft.modding.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

public abstract class OutputtingJavaExec extends JavaExec {

  public OutputtingJavaExec() {
    getJavaLauncher().set(getProject().getExtensions().getByType(JavaToolchainService.class).launcherFor(v -> v.getLanguageVersion().set(
        JavaLanguageVersion.of(8))));
  }

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
