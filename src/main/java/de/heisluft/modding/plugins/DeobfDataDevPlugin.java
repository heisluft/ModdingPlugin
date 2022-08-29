package de.heisluft.modding.plugins;

import de.heisluft.modding.extensions.ClassicMCExt;
import de.heisluft.modding.extensions.DeobfDataExt;
import de.heisluft.modding.tasks.Differ;
import de.heisluft.modding.tasks.Extract;
import de.heisluft.modding.tasks.OutputtingJavaExec;
import de.heisluft.modding.tasks.Patcher;
import de.heisluft.modding.util.Util;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

public class DeobfDataDevPlugin extends BasePlugin {
    @Override
    public void apply(Project project) {
        super.apply(project);
        project.getExtensions().create("deobfData", DeobfDataExt.class);

        TaskContainer tasks = project.getTasks();

        tasks.getByName("classes").dependsOn(tasks.getByName(mcSourceSet.getClassesTaskName()));

        Path deobfWorkspaceDir = project.file("deobf-workspace").toPath();
        Path frgChecksumFile = deobfWorkspaceDir.resolve("fergie.sha512");
        Path frgFile = deobfWorkspaceDir.resolve("fergie.frg");
        Path patchesDir = deobfWorkspaceDir.resolve("patches");
        try {
            Files.createDirectories(patchesDir);
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
            // This cant be a lambda because Gradle will shit itself otherwise
            //noinspection Convert2Lambda
            task.doLast("copyToMainDir", new Action<Task>() {
                @Override
                public void execute(@Nonnull Task t) {
                    try {
                        if (!Files.isRegularFile(frgFile)) {
                            Files.copy(task.getOutput().get().getAsFile().toPath(), frgFile);
                            Files.write(frgChecksumFile, Util.SHA_512.digest(Files.readAllBytes(frgFile)));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        });

        TaskProvider<OutputtingJavaExec> remapJar = tasks.register("remapJar", OutputtingJavaExec.class, task -> {
            task.getOutputs().upToDateWhen(t -> {
                try {
                    byte[] computed = Util.SHA_512.digest(Files.readAllBytes(frgFile));
                    // Don't cache if sha was deleted
                    boolean wasEqual = Files.isRegularFile(frgChecksumFile) && Arrays.equals(Files.readAllBytes(frgChecksumFile), computed);
                    if(!wasEqual) Files.write(frgChecksumFile, computed); // Update / write new checksum
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
            task.getOutputs().upToDateWhen(t -> !remapJar.get().getDidWork());
            task.args(
                    remapJar.get().getOutput().get(),
                    task.getOutput().get().getAsFile().getParentFile()
            );
        });

        tasks.withType(Patcher.class).getByName("applyCompilerPatches", task -> task.getPatchDir().set(patchesDir.toFile()));

        tasks.withType(Differ.class).getByName("genPatches", task -> {
            // This cant be a lambda because Gradle will shit itself otherwise
            //noinspection Convert2Lambda
            task.doLast(new Action<Task>() {
                @Override
                public void execute(@Nonnull Task t) {
                    Path patchesPath = task.getPatchDir().getAsFile().get().toPath();
                    try(Stream<Path> files = Files.walk(patchesPath)) {
                        files.forEach(path -> {
                            try {
                                Files.copy(path, patchesDir.resolve(path.getFileName().toString()));
                            } catch (IOException ex) {
                                throw new UncheckedIOException(ex);
                            }
                        });
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
            task.getBackupSrcDir().set(((Extract) tasks.getByName("extractSrc")).getOutput());
        });

        project.afterEvaluate(project1 -> remapJar.configure(task ->
            task.args(
                    "remap",
                    genATs.get().getOutput().get().getAsFile().exists() ? applyATs.get().getOutput() : ctorFixedMC,
                    frgFile.toAbsolutePath(),
                    "-o",
                    task.getOutput().get().getAsFile().getAbsolutePath()
            )));
    }
}
