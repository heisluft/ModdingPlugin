package de.heisluft.modding.plugins;

import de.heisluft.modding.extensions.ClassicMCExt;
import de.heisluft.modding.repo.MCRepo;
import de.heisluft.modding.tasks.*;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
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
    RemapTask remapJarFrg = tasks.withType(RemapTask.class).getByName("remapJarFrg", task ->
        task.getInput().set(ext.getVersion().flatMap(resolveMinecraftJar(project)))
    );

    RestoreMeta restoreMeta = tasks.withType(RestoreMeta.class).getByName("restoreMeta", task -> task.getInput().set(remapJarFrg.getOutput()));
    Zip2ZipCopy stripLibraries = tasks.withType(Zip2ZipCopy.class).getByName("stripLibraries", task -> task.getInput().set(restoreMeta.getOutput()));
    tasks.withType(ATApply.class).getByName("applyAts", task -> task.getInput().set(stripLibraries.getOutput()));

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

    Patcher applyCompilerPatches = tasks.withType(Patcher.class).getByName("applyCompilerPatches", task ->
      task.getPatchDir().set(extractData.get().getOutput().dir("patches"))
    );

    tasks.getByName("genPatches", task -> ((Differ) task).getBackupSrcDir().set(applyCompilerPatches.getOutput()));

    project.afterEvaluate(project1 -> {
      boolean srcRemapping = project1.getExtensions().getByType(ClassicMCExt.class).getMappingType().get().equals(SOURCE);
      tasks.withType(Patcher.class).getByName("applyCompilerPatches", task -> {
        File patchesDir = extractData.get().getOutput().get().dir("patches").getAsFile();
        if(patchesDir.isDirectory()) task.getPatchDir().set(srcRemapping ? renamedPatchesDir.toFile() : patchesDir);
      });
    });
  }
}