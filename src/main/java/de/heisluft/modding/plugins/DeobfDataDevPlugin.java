package de.heisluft.modding.plugins;

import de.heisluft.modding.tasks.Differ;
import de.heisluft.modding.tasks.Extract;
import de.heisluft.modding.tasks.OutputtingJavaExec;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class DeobfDataDevPlugin extends BasePlugin {
    @Override
    public void apply(Project project) {
        super.apply(project);
        TaskContainer tasks = project.getTasks();

        tasks.getByName("classes").dependsOn(tasks.getByName(mcSourceSet.getClassesTaskName()));

        File deobfWorkspaceDir = project.file("deobf-workspace");
        deobfWorkspaceDir.mkdirs();

        TaskProvider<OutputtingJavaExec> genATs = tasks.register("genATs", OutputtingJavaExec.class, task -> {
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("at.cfg");
            task.getMainClass().set("de.heisluft.reveng.ATGenerator");
            task.args(
                    ctorFixedMC,
                    task.getOutput().get().getAsFile().getAbsolutePath()
            );
        });

        TaskProvider<OutputtingJavaExec> applyATs = tasks.register("applyATs", OutputtingJavaExec.class, task -> {
            File inFile = genATs.get().getOutput().get().getAsFile();
            task.onlyIf(t -> inFile.exists());
            task.dependsOn(genATs);
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("minecraft.jar");
            task.getMainClass().set("de.heisluft.reveng.at.ATApplicator");
            task.args(
                    ctorFixedMC,
                    inFile.getAbsolutePath(),
                    task.getOutput().get().getAsFile().getAbsolutePath()
            );
        });

        TaskProvider<OutputtingJavaExec> genMappings = tasks.register("genMappings", OutputtingJavaExec.class, task -> {
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("mappings-generated.frg");
            task.getMainClass().set("de.heisluft.reveng.Remapper");
            task.args(
                    "map",
                    ctorFixedMC,
                    task.getOutput().get().getAsFile().getAbsolutePath()
            );
            task.doLast("copyToMainDir", t -> {
                File target = new File(deobfWorkspaceDir, "fergie.frg");
                if(!target.isFile()) {
                    try {
                        Files.copy(task.getOutput().get().getAsFile().toPath(), target.toPath());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        });

        TaskProvider<OutputtingJavaExec> remapJar = tasks.register("remapJar", OutputtingJavaExec.class, task -> {
            task.getOutputs().upToDateWhen(t -> false);
            task.dependsOn(genMappings, applyATs);
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("minecraft.jar");
            task.getMainClass().set("de.heisluft.reveng.Remapper");

            task.doFirst(t -> {
                task.args(
                        "remap",
                        genATs.get().getOutput().get().getAsFile().exists() ? applyATs.get().getOutput() : ctorFixedMC,
                        new File(deobfWorkspaceDir, "fergie.frg").getAbsolutePath(),
                        "-o",
                        task.getOutput().get().getAsFile().getAbsolutePath()
                );
            });
        });

        tasks.withType(OutputtingJavaExec.class).getByName("decompMC", task -> {
            task.dependsOn(remapJar);
            task.args(
                    remapJar.get().getOutput().get(),
                    task.getOutput().get().getAsFile().getParentFile()
            );
        });

        Extract extractSrc = (Extract)tasks.getByName("extractSrc");

        tasks.withType(Differ.class).getByName("genPatches", task -> {
            task.getBackupSrcDir().set(extractSrc.getOutput());
        });

        tasks.register("copySrc", Copy.class, task -> {
            task.dependsOn(extractSrc);
            task.into(mcSourceSet.getJava().getSrcDirs().iterator().next());
            task.from(extractSrc.getOutput());
            task.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
        });
    }
}
