package de.heisluft.modding.plugins;

import de.heisluft.modding.extensions.ClassicMCExt;
import de.heisluft.modding.repo.MCRepo;
import de.heisluft.modding.tasks.*;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.nio.file.Path;

import static de.heisluft.modding.extensions.ClassicMCExt.SOURCE;

public class JarModDevPlugin extends BasePlugin {

  @Override
  public void apply(Project project) {
    super.apply(project);

    Path renamedPatchesDir = project.getLayout().getBuildDirectory().getAsFile().get().toPath().resolve("renamePatches");

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
      ClassicMCExt ext = project1.getExtensions().getByType(ClassicMCExt.class);
      boolean srcRemapping = ext.getMappingType().equals(SOURCE);

      // We have to remap first as the Remapper cannot infer inheritance information for obfuscated
      // libs after they are stripped
      RemapTask ta = tasks.withType(RemapTask.class).getByName("remapJarFrg", t -> {
        try {
          t.getInput().set(MCRepo.getInstance().resolve("minecraft", ext.getVersion().get()).toFile());
        } catch(IOException e) {
          throw new RuntimeException(e);
        }
      });
      RestoreMeta ta1 = tasks.withType(RestoreMeta.class).getByName("restoreMeta", task -> task.getInput().set(ta.getOutput()));
      Zip2ZipCopy ta2 = tasks.withType(Zip2ZipCopy.class).getByName("stripLibraries", task -> task.getInput().set(ta1.getOutput()));
      tasks.withType(ATApply.class).getByName("applyAts", task -> task.getInput().set(ta2.getOutput()));
      tasks.withType(Patcher.class).getByName("applyCompilerPatches", task -> task.getPatchDir().set(srcRemapping ? renamedPatchesDir.toFile() : extractData.get().getOutput().get().dir("patches").getAsFile()));

    });
  }
}