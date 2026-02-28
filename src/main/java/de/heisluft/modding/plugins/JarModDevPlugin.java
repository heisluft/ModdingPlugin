package de.heisluft.modding.plugins;

import de.heisluft.modding.extensions.ClassicMCExt;
import de.heisluft.modding.tasks.*;
import org.gradle.api.Project;
import org.gradle.api.tasks.*;

import java.io.File;
import java.nio.file.Path;

import static de.heisluft.modding.extensions.ClassicMCExt.SOURCE;

public class JarModDevPlugin extends BasePlugin {

  @Override
  public void apply(Project project) {
    super.apply(project);

    Path renamedPatchesDir = project.getLayout().getBuildDirectory().getAsFile().get().toPath().resolve("renamePatches");
    ClassicMCExt ext =  project.getExtensions().getByType(ClassicMCExt.class);
    TaskContainer tasks = project.getTasks();

    // We have to remap first as the Remapper cannot infer inheritance information for obfuscated
    // libs after they are stripped
    TaskProvider<RemapTask> remapJarFrg = tasks.named("remapJarFrg", RemapTask.class, task ->
        task.getInput().set(ext.getVersion().flatMap(resolveMinecraftJar(project)))
    );

    TaskProvider<RestoreMeta> restoreMeta = tasks.named("restoreMeta", RestoreMeta.class, task ->
        task.getInput().set(remapJarFrg.flatMap(OutputtingJavaExec::getOutput))
    );

    TaskProvider<Zip2ZipCopy> stripLibraries = tasks.named("stripLibraries", Zip2ZipCopy.class, task ->
        task.getInput().set(restoreMeta.flatMap(OutputtingJavaExec::getOutput))
    );

    tasks.named("applyAts", ATApply.class, task ->
        task.getInput().set(stripLibraries.flatMap(Zip2ZipCopy::getOutput))
    );

    TaskProvider<MavenDownload> downloadDeobfData = tasks.register("downloadDeobfData", MavenDownload.class, task -> {
      task.getGroupName().set("de.heisluft.deobf.data");
      task.getArtifactName().set(project.getExtensions().getByType(ClassicMCExt.class).getVersion());
      task.getExtension().set("zip");
      task.getMavenRepoUrl().set(REPO_URL);
    });

    TaskProvider<Extract> extractData = tasks.register("extractData", Extract.class, task ->
      task.getInput().set(downloadDeobfData.flatMap(MavenDownload::getOutput))
    );

    TaskProvider<OutputtingJavaExec> createFrg2SrcMappings = tasks.named("createFrg2SrcMappings", OutputtingJavaExec.class, task -> {
      task.dependsOn(extractData);
      task.getArgumentProviders().add(resolving(
          "genMediatorMappings",
          extractData.flatMap(t -> t.getOutput().file("fergie.frg")),
          extractData.flatMap(t -> t.getOutput().file("src.frg")),
          "-o", task.getOutput()
      ));
    });

    tasks.named("renamePatches", JavaExec.class, task -> {
          task.dependsOn(createFrg2SrcMappings);
          task.getArgumentProviders().add(resolving(
              extractData.flatMap(e -> e.getOutput().dir("patches")),
              createFrg2SrcMappings.flatMap(OutputtingJavaExec::getOutput),
              renamedPatchesDir
          ));
        }
    );

    tasks.named("remapJarFrg", RemapTask.class, task -> {
      task.getMappings().set(extractData.flatMap(t -> t.getOutput().file("fergie.frg")));
    });

    tasks.named("applyAts", ATApply.class, task ->
        task.getATFile().set(extractData.flatMap(t -> t.getOutput().file("at.cfg")))
    );

    TaskProvider<Patcher> applyCompilerPatches = tasks.named("applyCompilerPatches", Patcher.class, task ->
      task.getPatchDir().set(extractData.flatMap(t -> t.getOutput().dir("patches")))
    );

    tasks.named("genPatches", Differ.class, task ->
        task.getBackupSrcDir().set(applyCompilerPatches.flatMap(Patcher::getOutput))
    );

    project.afterEvaluate(project1 -> {
      boolean srcRemapping = project1.getExtensions().getByType(ClassicMCExt.class).getMappingType().get().equals(SOURCE);
      tasks.named("applyCompilerPatches", Patcher.class, task -> {
        File patchesDir = extractData.get().getOutput().get().dir("patches").getAsFile();
        if(patchesDir.isDirectory()) task.getPatchDir().set(srcRemapping ? renamedPatchesDir.toFile() : patchesDir);
      });
    });
  }
}