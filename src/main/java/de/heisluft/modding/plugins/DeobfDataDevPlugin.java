package de.heisluft.modding.plugins;

import de.heisluft.modding.tasks.Differ;
import de.heisluft.modding.tasks.Extract;
import de.heisluft.modding.tasks.OutputtingJavaExec;
import de.heisluft.modding.util.Util;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class DeobfDataDevPlugin extends BasePlugin {
    @Override
    public void apply(Project project) {
        super.apply(project);
        TaskContainer tasks = project.getTasks();

        tasks.getByName("classes").dependsOn(tasks.getByName(mcSourceSet.getClassesTaskName()));

        Path deobfWorkspaceDir = project.file("deobf-workspace").toPath();
        Path frgChecksumFile = deobfWorkspaceDir.resolve("fergie.sha512");
        Path frgFile = deobfWorkspaceDir.resolve("fergie.frg");
        try {
            Files.createDirectories(deobfWorkspaceDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

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
                    try {
                        if(!Files.isRegularFile(frgFile)) {
                            Files.copy(task.getOutput().get().getAsFile().toPath(), frgFile);
                            Files.write(frgChecksumFile, Util.SHA_512.digest(Files.readAllBytes(frgFile)));
                        }
                        if(!Files.isRegularFile(frgChecksumFile)) // recreate if necessary
                            Files.write(frgChecksumFile, Util.SHA_512.digest(Files.readAllBytes(frgFile)));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
            });
        });

        TaskProvider<OutputtingJavaExec> remapJar = tasks.register("remapJar", OutputtingJavaExec.class, task -> {
            task.getOutputs().upToDateWhen(t -> {
                try {
                    byte[] computed = Util.SHA_512.digest(Files.readAllBytes(frgFile));
                    boolean wasEqual =  Arrays.equals(Files.readAllBytes(frgChecksumFile), computed);
                    if(!wasEqual) Files.write(frgChecksumFile, computed); // Update checksum
                    return wasEqual;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            task.dependsOn(genMappings, applyATs);
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("minecraft.jar");
            task.getMainClass().set("de.heisluft.reveng.Remapper");
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

        project.afterEvaluate(project1 -> {
            remapJar.configure(task ->
                task.args(
                        "remap",
                        genATs.get().getOutput().get().getAsFile().exists() ? applyATs.get().getOutput() : ctorFixedMC,
                        frgFile.toAbsolutePath(),
                        "-o",
                        task.getOutput().get().getAsFile().getAbsolutePath()
                ));
        });
    }
}
