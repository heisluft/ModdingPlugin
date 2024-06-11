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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static de.heisluft.modding.extensions.ClassicMCExt.SOURCE;

public class JarModDevPlugin extends BasePlugin {

  @Override
  public void apply(Project project) {
    super.apply(project);

    Path renamedPatchesDir = project.getBuildDir().toPath().resolve("renamePatches");

    TaskContainer tasks = project.getTasks();

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

    tasks.withType(RemapTask.class).getByName("remapJarFrg", task -> {
      task.dependsOn(extractData);
      task.getMappings().set(extractData.get().getOutput().get().file("fergie.frg"));
    });
    tasks.withType(ATApply.class).getByName("applyAts", task -> task.getATFile().set(extractData.get().getOutput().get().file("at.cfg")));

    Patcher applyCompilerPatches = tasks.withType(Patcher.class).getByName("applyCompilerPatches", task -> {
      task.getPatchDir().set(extractData.get().getOutput().dir("patches"));
    });

    tasks.getByName("genPatches", task -> ((Differ) task).getBackupSrcDir().set(applyCompilerPatches.getOutput()));

    project.afterEvaluate(project1 -> {
      boolean srcRemapping = project1.getExtensions().getByType(ClassicMCExt.class).getMappingType().equals(SOURCE);
      tasks.withType(Patcher.class).getByName("applyCompilerPatches", task -> task.getPatchDir().set(srcRemapping ? renamedPatchesDir.toFile() : extractData.get().getOutput().get().dir("patches").getAsFile()));
    });
  }
}