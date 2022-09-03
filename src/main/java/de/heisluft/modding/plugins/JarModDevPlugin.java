package de.heisluft.modding.plugins;

import de.heisluft.modding.extensions.ClassicMCExt;
import de.heisluft.modding.tasks.*;
import de.heisluft.modding.util.Util;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.tasks.*;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;

import static de.heisluft.modding.extensions.ClassicMCExt.SOURCE;

//TODO: Either rename ATs or Rename Jar twice
public class JarModDevPlugin extends BasePlugin {

  @Override
  public void apply(Project project) {
    super.apply(project);

    Path renamedPatchesDir = project.getBuildDir().toPath().resolve("renamePatches");

    TaskContainer tasks = project.getTasks();

    tasks.getByName("classes").dependsOn(tasks.getByName(mcSourceSet.getClassesTaskName()));

    TaskProvider<MavenDownload> downloadDeobfData = tasks.register("downloadDeobfData", MavenDownload.class, task -> {
      task.getGroupName().set("de.heisluft.deobf.data");
      task.getArtifactName().set(project.getExtensions().getByType(ClassicMCExt.class).getVersion());
      task.getExtension().set("zip");
      task.getMavenRepoUrl().set(REPO_URL);
    });

    TaskProvider<Extract> extractData = tasks.register("extractData", Extract.class, task -> {
      task.dependsOn(downloadDeobfData);
      task.getInput().set(downloadDeobfData.get().getOutput());
      // This cant be a lambda because Gradle will shit itself otherwise
      //noinspection Convert2Lambda
      task.doFirst(new Action<Task>() { // If work needs to be done, we have to first purge the output
        @Override
        public void execute(@Nonnull Task task) {
          try {
            Util.deleteContents(((Extract)task).getOutput().getAsFile().get());
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      });
    });

    OutputtingJavaExec createFrg2SrcMappings  = tasks.withType(OutputtingJavaExec.class).getByName("createFrg2SrcMappings", task -> {
      Directory out = extractData.get().getOutput().get();
      task.args("genMediatorMappings", out.file("fergie.frg"), out.file("src.frg"), "-o", task.getOutput().get());
    });

    tasks.withType(JavaExec.class).getByName("renamePatches", task -> task.args(extractData.get().getOutput().get().dir("patches"), createFrg2SrcMappings.getOutput().get(), renamedPatchesDir));

    TaskProvider<OutputtingJavaExec> remapJar = tasks.register("remapJar", OutputtingJavaExec.class, task -> {
      task.dependsOn(extractData);
      task.classpath(deobfToolsJarFile);
      task.setOutputFilename("minecraft.jar");
      task.getMainClass().set("de.heisluft.reveng.Remapper");
    });

    TaskProvider<OutputtingJavaExec> applyAts = tasks.register("applyAts", OutputtingJavaExec.class, task -> {
      task.dependsOn(remapJar);
      task.classpath(deobfToolsJarFile);
      task.setOutputFilename("minecraft.jar");
      task.getMainClass().set("de.heisluft.reveng.at.ATApplicator");
      task.args(
              remapJar.get().getOutput().get(),
              new File(extractData.get().getOutput().getAsFile().get(), "at.cfg"),
              task.getOutput().get()
      );
    });

    TaskProvider<Zip2ZipCopy> stripLibs = tasks.register("stripLibraries", Zip2ZipCopy.class, task -> {
      task.dependsOn(applyAts);
      task.getInput().set(applyAts.get().getOutput());
      task.getOutput().set(new File(project.getBuildDir(), task.getName() + File.separator + "minecraft.jar"));
      task.getIncludedPaths().addAll(Arrays.asList("util/**", "com/**"));
    });

    tasks.withType(OutputtingJavaExec.class).getByName("decompMC", task -> {
      task.dependsOn(stripLibs);
      task.args(
              stripLibs.get().getOutput().get(),
              task.getOutput().get().getAsFile().getParentFile()
      );
    });

    Patcher applyCompilerPatches = tasks.withType(Patcher.class).getByName("applyCompilerPatches", task -> {
      task.getPatchDir().set(extractData.get().getOutput().dir("patches"));
    });

    tasks.getByName("genPatches", task -> ((Differ) task).getBackupSrcDir().set(applyCompilerPatches.getOutput()));

    project.afterEvaluate(project1 -> {
      boolean srcRemapping = project1.getExtensions().getByType(ClassicMCExt.class).getMappingType().equals(SOURCE);

      tasks.withType(Patcher.class).getByName("applyCompilerPatches", task -> task.getPatchDir().set(srcRemapping ? renamedPatchesDir.toFile() : extractData.get().getOutput().get().dir("patches").getAsFile()));

      remapJar.configure(task -> {
        Directory mappingsDir = extractData.get().getOutput().get();
        task.args(
                "remap",
                ctorFixedMC,
                (srcRemapping ? mappingsDir.file("src.frg") : mappingsDir.file("fergie.frg")),
                "-o",
                task.getOutput().get().getAsFile().getAbsolutePath()
        );
      });
    });
  }
}