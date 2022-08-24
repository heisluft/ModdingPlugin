package de.heisluft.modding.plugins;

import de.heisluft.modding.Ext;
import de.heisluft.modding.tasks.*;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.*;

import java.io.File;
import java.util.Arrays;

public class JarModDevPlugin extends BasePlugin {

  @Override
  public void apply(Project project) {
    super.apply(project);
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

    tasks.withType(OutputtingJavaExec.class).getByName("decompMC", task -> {
      task.dependsOn(applyAts);
      task.args(
              applyAts.get().getOutput().get(),
              task.getOutput().get().getAsFile().getParentFile()
      );

    });

    Extract extractSrc = (Extract) tasks.getByName("extractSrc");

    TaskProvider<Patcher> applyCompilerPatches = tasks.register("applyCompilerPatches", Patcher.class, task -> {
      task.dependsOn(extractSrc);
      task.getPatchDir().set(extractData.get().getOutput().dir("patches"));
      task.getInput().set(extractSrc.getOutput());
    });

    tasks.register("copySrc", Copy.class, task -> {
      task.dependsOn(applyCompilerPatches);
      task.into(mcSourceSet.getJava().getSrcDirs().iterator().next());
      task.from(applyCompilerPatches.get().getOutput());
      task.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
    });

    tasks.getByName("genPatches", task -> ((Differ) task).getBackupSrcDir().set(applyCompilerPatches.get().getOutput()));
  }
}