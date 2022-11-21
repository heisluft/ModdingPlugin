package de.heisluft.modding.plugins;

import de.heisluft.modding.extensions.ClassicMCExt;
import de.heisluft.modding.extensions.DeobfDataExt;
import de.heisluft.modding.tasks.Differ;
import de.heisluft.modding.tasks.Extract;
import de.heisluft.modding.tasks.OutputtingJavaExec;
import de.heisluft.modding.tasks.Patcher;
import de.heisluft.modding.tasks.Zip2ZipCopy;
import de.heisluft.modding.util.Util;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import static de.heisluft.modding.extensions.ClassicMCExt.FERGIE;
import static de.heisluft.modding.extensions.ClassicMCExt.SOURCE;

public class DeobfDataDevPlugin extends BasePlugin {
    @Override
    public void apply(Project project) {
        super.apply(project);
        project.getExtensions().create("deobfData", DeobfDataExt.class);

        TaskContainer tasks = project.getTasks();

        tasks.getByName("classes").dependsOn(tasks.getByName(mcSourceSet.getClassesTaskName()));

        Path deobfWorkspaceDir = project.file("deobf-workspace").toPath();
        Path frgMappingsFile = deobfWorkspaceDir.resolve("fergie.frg");
        Path frgChecksumFile = deobfWorkspaceDir.resolve("fergie.sha512");
        Path srcMappingsFile = deobfWorkspaceDir.resolve("src.frg");
        Path srcChecksumFile = deobfWorkspaceDir.resolve("src.sha512");
        Path patchesDir = deobfWorkspaceDir.resolve("patches");
        Path renamedPatchesDir = project.getBuildDir().toPath().resolve("renamePatches");

        try {
            Files.createDirectories(patchesDir);
            Files.createDirectories(renamedPatchesDir);
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
                        if (!Files.isRegularFile(frgMappingsFile)) {
                            Files.copy(task.getOutput().get().getAsFile().toPath(), frgMappingsFile);
                            Files.write(frgChecksumFile, Util.SHA_512.digest(Files.readAllBytes(frgMappingsFile)));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        });

        TaskProvider<OutputtingJavaExec> remapJar = tasks.register("remapJar", OutputtingJavaExec.class, task -> {
            task.dependsOn(applyATs);
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("minecraft.jar");
            task.getMainClass().set("de.heisluft.reveng.Remapper");
        });

        TaskProvider<Zip2ZipCopy> stripLibs = tasks.register("stripLibraries", Zip2ZipCopy.class, task -> {
            task.dependsOn(remapJar);
            task.getInput().set(remapJar.get().getOutput());
            task.getOutputs().upToDateWhen(t -> !remapJar.get().getDidWork());
            task.getOutput().set(new File(project.getBuildDir(), task.getName() + File.separator + "minecraft.jar"));
            task.getIncludedPaths().addAll(Arrays.asList("util/**", "com/mojang/**", "net/minecraft/**"));
        });

        tasks.withType(OutputtingJavaExec.class).getByName("decompMC", task -> {
            task.dependsOn(stripLibs);
            task.args(
                    stripLibs.get().getOutput().get(),
                    task.getOutput().get().getAsFile().getParentFile()
            );
        });

        OutputtingJavaExec createFrg2SrcMappings  = tasks.withType(OutputtingJavaExec.class).getByName("createFrg2SrcMappings", task -> {
            task.getOutputs().upToDateWhen(t -> validateChecksumUpdating(frgMappingsFile, frgChecksumFile) && validateChecksumUpdating(srcMappingsFile, srcChecksumFile));
            task.args("genMediatorMappings", frgMappingsFile, srcMappingsFile, "-o", task.getOutput().get());
        });

        tasks.withType(JavaExec.class).getByName("renamePatches", task -> task.args(patchesDir, createFrg2SrcMappings.getOutput().get(), renamedPatchesDir));

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



        project.afterEvaluate(project1 -> {
            boolean srcRemapping = Objects.equals(project1.getExtensions().getByType(ClassicMCExt.class).getMappingType(), SOURCE);

            if(srcRemapping) {
                try {
                    if (!Files.isRegularFile(srcMappingsFile)) {
                        Files.copy(frgMappingsFile, srcMappingsFile);
                        Files.write(srcChecksumFile, Util.SHA_512.digest(Files.readAllBytes(srcMappingsFile)));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            tasks.withType(Patcher.class).getByName("applyCompilerPatches", task -> task.getPatchDir().set((srcRemapping ? renamedPatchesDir : patchesDir).toFile()));

            remapJar.configure(task -> {
                if(!srcRemapping) task.dependsOn(genMappings);
                task.getOutputs().upToDateWhen(t -> srcRemapping ? validateChecksumUpdating(srcMappingsFile, srcChecksumFile) : validateChecksumUpdating(frgMappingsFile, frgChecksumFile));
                task.args(
                        "remap",
                        genATs.get().getOutput().get().getAsFile().exists() ? applyATs.get().getOutput() : ctorFixedMC,
                        (srcRemapping ? srcMappingsFile : frgMappingsFile).toAbsolutePath(),
                        "-o",
                        task.getOutput().get().getAsFile().getAbsolutePath()
                );
            });
        });
    }

    private boolean validateChecksumUpdating(Path path, Path checksumPath) {
        try {
            byte[] computed = Util.SHA_512.digest(Files.readAllBytes(path));
            // Don't cache if sha was deleted
            boolean wasEqual = Files.isRegularFile(checksumPath) && Arrays.equals(Files.readAllBytes(checksumPath), computed);
            if(!wasEqual) Files.write(checksumPath, computed); // Update / write new checksum
            return wasEqual;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
