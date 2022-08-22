package de.heisluft.modding.plugins;

import de.heisluft.modding.tasks.Extract;
import de.heisluft.modding.tasks.OutputtingJavaExec;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class DeobfDataDevPlugin extends BasePlugin {
    @Override
    public void apply(Project project) {
        super.apply(project);
        TaskContainer tasks = project.getTasks();

        tasks.getByName("classes").dependsOn(tasks.getByName(mcSourceSet.getClassesTaskName()));

        // for task dependency management
        OutputtingJavaExec fixConstructors = tasks.withType(OutputtingJavaExec.class).getByName("fixConstructors");

        File deobfWorkspaceDir = project.file("deobf-workspace");
        deobfWorkspaceDir.mkdirs();

        TaskProvider<OutputtingJavaExec> gATT = tasks.register("genATsTemp", OutputtingJavaExec.class, task -> {
            task.dependsOn(fixConstructors);
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("at.cfg");
            task.getMainClass().set("de.heisluft.reveng.ATGenerator");
            task.args(
                    fixConstructors.getOutput().get().getAsFile().getAbsolutePath(),
                    task.getOutput().get().getAsFile().getAbsolutePath()
            );
        });

        TaskProvider<OutputtingJavaExec> aATT = tasks.register("applyATsTemp", OutputtingJavaExec.class, task -> {
            File inFile = gATT.get().getOutput().get().getAsFile();
            task.onlyIf(t -> inFile.exists());
            task.dependsOn(gATT);
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("minecraft.jar");
            task.getMainClass().set("de.heisluft.reveng.at.ATApplicator");
            task.args(
                    fixConstructors.getOutput().get().getAsFile().getAbsolutePath(),
                    inFile.getAbsolutePath(),
                    task.getOutput().get().getAsFile().getAbsolutePath()
            );
        });

        TaskProvider<OutputtingJavaExec> gMT = tasks.register("genMappingsTemp", OutputtingJavaExec.class, task -> {
            task.dependsOn(fixConstructors);
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("mappings-generated.frg");
            task.getMainClass().set("de.heisluft.reveng.Remapper");
            task.args(
                    "map",
                    fixConstructors.getOutput().get().getAsFile().getAbsolutePath(),
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

        TaskProvider<OutputtingJavaExec> rJFT = tasks.register("remapJarFrgTemp", OutputtingJavaExec.class, task -> {
            task.getOutputs().upToDateWhen(t -> false);
            task.dependsOn(gMT, aATT);
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("minecraft.jar");
            task.getMainClass().set("de.heisluft.reveng.Remapper");

            task.doFirst(t -> {
                RegularFileProperty inFile = gATT.get().getOutput().get().getAsFile().exists() ? aATT.get().getOutput() : fixConstructors.getOutput();
                task.args(
                        "remap",
                        inFile.get().getAsFile().getAbsolutePath(),
                        new File(deobfWorkspaceDir, "fergie.frg").getAbsolutePath(),
                        "-o",
                        task.getOutput().get().getAsFile().getAbsolutePath()
                );
            });
        });

        TaskProvider<OutputtingJavaExec> decompMC = tasks.register("decompMC", OutputtingJavaExec.class, task -> {
            task.dependsOn(rJFT);
            task.setOutputFilename("minecraft.jar");
            task.classpath(fernFlowerJarFile);
            task.getMainClass().set("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler");
            task.setMaxHeapSize("4G");
            task.getJavaLauncher().set(project.getExtensions().getByType(JavaToolchainService.class).launcherFor(v -> v.getLanguageVersion().set(JavaLanguageVersion.of(11))));
            task.args(
                    rJFT.get().getOutput().get().getAsFile().getAbsolutePath(),
                    task.getOutput().get().getAsFile().toPath().getParent().toAbsolutePath().toString()
            );
        });
        TaskProvider<Extract> extractSrc = tasks.register("extractSrc", Extract.class, task -> {
            task.dependsOn(decompMC);
            task.getInput().set(decompMC.get().getOutput());
        });
        tasks.register("copySrc", Copy.class, task -> {
            task.dependsOn(extractSrc);
            task.into(mcSourceSet.getJava().getSrcDirs().iterator().next());
            task.from(extractSrc.get().getOutput());
            task.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
        });
    }
}
