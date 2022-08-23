package de.heisluft.modding.plugins;

import de.heisluft.modding.Ext;
import de.heisluft.modding.tasks.*;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.*;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.File;
import java.util.Arrays;

public class JarModDevPlugin extends BasePlugin {

  @Override
  public void apply(Project project) {
    super.apply(project);
    JavaToolchainService service = project.getExtensions().getByType(JavaToolchainService.class);
    TaskContainer tasks = project.getTasks();

    tasks.getByName("classes").dependsOn(tasks.getByName(mcSourceSet.getClassesTaskName()));

    TaskProvider<MavenDownload> downloadDeobfData = tasks.register("downloadDeobfData", MavenDownload.class, task -> {
      task.getGroupName().set("de.heisluft.deobf.data");
      task.getArtifactName().set(project.getExtensions().getByType(Ext.class).getVersion());
      task.getExtension().set("zip");
      task.getMavenRepoUrl().set(REPO_URL);
    });
    TaskProvider<Extract> extractData = tasks.register("extractData", Extract.class, task -> {
      task.dependsOn(downloadDeobfData);
      task.getInput().set(downloadDeobfData.get().getOutput());
      task.getIncludedPaths().addAll(Arrays.asList("fergie.frg", "at.cfg", "patches/*"));
    });
    TaskProvider<OutputtingJavaExec> remapJar = tasks.register("remapJar", OutputtingJavaExec.class, task -> {
      task.dependsOn(extractData);
      task.classpath(deobfToolsJarFile);
      task.setOutputFilename("minecraft.jar");
      task.getMainClass().set("de.heisluft.reveng.Remapper");
      task.args(
          "remap",
          ctorFixedMC,
          new File(extractData.get().getOutput().getAsFile().get(), "fergie.frg").getAbsolutePath(),
          "-o",
          task.getOutput().get().getAsFile().getAbsolutePath()
      );
    });
    TaskProvider<Zip2ZipCopy> stripLibs = tasks.register("stripLibraries", Zip2ZipCopy.class, task -> {
      task.dependsOn(remapJar);
      task.getInput().set(remapJar.get().getOutput());
      task.getIncludedPaths().addAll(Arrays.asList("util/**", "com/**"));
    });
    TaskProvider<OutputtingJavaExec> applyAts = tasks.register("applyAts", OutputtingJavaExec.class, task -> {
      task.dependsOn(stripLibs);
      task.classpath(deobfToolsJarFile);
      task.setOutputFilename("minecraft.jar");
      task.getMainClass().set("de.heisluft.reveng.at.ATApplicator");
      task.args(
          stripLibs.get().getOutput().getAsFile().get().getAbsolutePath(),
          new File(extractData.get().getOutput().getAsFile().get(), "at.cfg"),
          task.getOutput().getAsFile().get().getAbsolutePath()
      );
    });
    TaskProvider<OutputtingJavaExec> decompMC = tasks.register("decompMC", OutputtingJavaExec.class, task -> {
      task.dependsOn(applyAts);
      task.setOutputFilename("minecraft.jar");
      task.classpath(fernFlowerJarFile);
      task.getMainClass().set("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler");
      task.setMaxHeapSize("4G");
      task.getJavaLauncher().set(service.launcherFor(versionOf(11)));
      task.args(
          applyAts.get().getOutput().get().getAsFile().getAbsolutePath(),
          task.getOutput().get().getAsFile().toPath().getParent().toAbsolutePath().toString()
      );
    });
    TaskProvider<Extract> extractSrc = tasks.register("extractSrc", Extract.class, task -> {
      task.dependsOn(decompMC);
      task.getInput().set(decompMC.get().getOutput());
    });
    TaskProvider<Patcher> applyCompilerPatches = tasks.register("applyCompilerPatches", Patcher.class, task -> {
      task.dependsOn(extractSrc);
      task.getPatchDir().set(extractData.get().getOutput().dir("patches"));
      task.getInput().set(extractSrc.get().getOutput());
    });
    TaskProvider<Copy> copySrc = tasks.register("copySrc", Copy.class, task -> {
      task.dependsOn(applyCompilerPatches);
      task.into(mcSourceSet.getJava().getSrcDirs().iterator().next());
      task.from(applyCompilerPatches.get().getOutput());
      task.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
    });
    tasks.register("genPatches", Differ.class, task -> {
      task.dependsOn(copySrc);
      task.getBackupSrcDir().set(applyCompilerPatches.get().getOutput());
      task.getModifiedSrcDir().set(copySrc.get().getDestinationDir());
    });
  }
}