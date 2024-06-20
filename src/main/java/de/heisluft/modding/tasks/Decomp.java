package de.heisluft.modding.tasks;

import de.heisluft.modding.util.Util;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.File;
import java.io.IOException;

public abstract class Decomp extends JavaExec {

  public Decomp() {
    getMainClass().set("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler");
    setMaxHeapSize("4G");
    getJavaLauncher().set(getProject().getExtensions().getByType(JavaToolchainService.class).launcherFor(v -> v.getLanguageVersion().set(JavaLanguageVersion.of(11))));
    getOutput().convention(() -> new File(getProject().getBuildDir(), getName() + "/" + getInput().getAsFile().get().getName()));
  }

  @InputFile
  public abstract RegularFileProperty getInput();

  @OutputFile
  public abstract RegularFileProperty getOutput();

  @TaskAction
  @Override
  public void exec() {
    try {
      Util.deleteContents(new File(getProject().getBuildDir(), getName()));
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
    args(
        getInput().get().getAsFile().getAbsolutePath(),
        getOutput().get().getAsFile().getParentFile().getAbsolutePath()
    );
    super.exec();
  }
}
