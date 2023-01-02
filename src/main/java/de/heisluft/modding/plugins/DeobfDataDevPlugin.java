package de.heisluft.modding.plugins;

import de.heisluft.modding.extensions.ClassicMCExt;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static de.heisluft.modding.extensions.ClassicMCExt.SOURCE;

public class DeobfDataDevPlugin extends BasePlugin {
    @Override
    public void apply(Project project) {
        super.apply(project);

        TaskContainer tasks = project.getTasks();

        tasks.getByName("classes").dependsOn(tasks.getByName(mcSourceSet.getClassesTaskName()));

        Path deobfWorkspaceDir = project.file("deobf-workspace").toPath();
        Path frgMappingsFile = deobfWorkspaceDir.resolve("fergie.frg");
        Path frgChecksumFile = deobfWorkspaceDir.resolve("fergie.sha512");
        Path srcMappingsFile = deobfWorkspaceDir.resolve("src.frg");
        Path srcChecksumFile = deobfWorkspaceDir.resolve("src.sha512");
        Path atFile = deobfWorkspaceDir.resolve("at.cfg");
        Path atChecksumFile = deobfWorkspaceDir.resolve("at.sha512");
        Path patchesDir = deobfWorkspaceDir.resolve("patches");
        Path renamedPatchesDir = project.getBuildDir().toPath().resolve("renamePatches");

        OutputtingJavaExec restoreMeta = tasks.withType(OutputtingJavaExec.class).getByName("restoreMeta", task -> {
            List<String> args = task.getArgs();
            args.add(new File(project.getBuildDir(), task.getName() + "/mappings.frg").getAbsolutePath());
            task.setArgs(args);
        });

        try {
            Files.createDirectories(patchesDir);
            Files.createDirectories(renamedPatchesDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        TaskProvider<OutputtingJavaExec> genATs = tasks.register("genATs", OutputtingJavaExec.class, task -> {
            task.dependsOn(restoreMeta);
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("at.cfg");
            task.getMainClass().set("de.heisluft.deobf.tooling.at.ATGenerator");
            task.args(
                    restoreMeta.getOutput().get().getAsFile().getAbsolutePath(),
                    task.getOutput().get().getAsFile().getAbsolutePath()
            );
            // This cant be a lambda because Gradle will shit itself otherwise
            //noinspection Convert2Lambda
            task.doLast("copyToMainDir", new Action<Task>() {
                @Override
                public void execute(@Nonnull Task t) {
                    try {
                        if (!Files.isRegularFile(atFile)) {
                            Files.copy(task.getOutput().get().getAsFile().toPath(), atFile);
                            Files.write(atChecksumFile, Util.SHA_512.digest(Files.readAllBytes(atFile)));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        });

        TaskProvider<OutputtingJavaExec> applyATs = tasks.register("applyATs", OutputtingJavaExec.class, task -> {
            task.dependsOn(genATs);
            task.getOutputs().upToDateWhen(t -> validateChecksumUpdating(atFile, atChecksumFile));
            task.onlyIf(t -> Files.isRegularFile(atFile));
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("minecraft.jar");
            task.getMainClass().set("de.heisluft.deobf.tooling.at.ATApplicator");
            task.args(
                    restoreMeta.getOutput().get().getAsFile().getAbsolutePath(),
                    atFile.toAbsolutePath(),
                    task.getOutput().get().getAsFile().getAbsolutePath()
            );
        });

        TaskProvider<OutputtingJavaExec> genMappings = tasks.register("genMappings", OutputtingJavaExec.class, task -> {
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("mappings-generated.frg");
            task.getMainClass().set("de.heisluft.deobf.tooling.Remapper");
            task.args(
                    "map",
                    restoreMeta.getOutput().get().getAsFile().getAbsolutePath(),
                    task.getOutput().get().getAsFile().getAbsolutePath(),
                    "-s",
                    new File(project.getBuildDir(), restoreMeta.getName() + "/mappings.frg").getAbsolutePath()
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
            task.getMainClass().set("de.heisluft.deobf.tooling.Remapper");
        });

        TaskProvider<Zip2ZipCopy> stripClassicLibs = tasks.register("stripClassicLibraries", Zip2ZipCopy.class, task -> {
            task.dependsOn(remapJar);
            task.getInput().set(remapJar.get().getOutput());
            task.getOutputs().upToDateWhen(t -> !remapJar.get().getDidWork());
            task.getOutput().set(new File(project.getBuildDir(), task.getName() + File.separator + "minecraft.jar"));
            task.getIncludedPaths().addAll(Arrays.asList("a/**", "com/mojang/**"));
            // This cant be a lambda because Gradle will shit itself otherwise
            //noinspection Convert2Lambda
            task.doFirst(new Action<Task>() { // If work needs to be done, we have to first purge the output
                @Override
                public void execute(@Nonnull Task task) {
                    try {
                        Files.deleteIfExists(((Zip2ZipCopy)task).getOutput().getAsFile().get().toPath());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        });

        tasks.withType(OutputtingJavaExec.class).getByName("decompMC", task -> {
            task.dependsOn(stripClassicLibs);
            task.getOutputs().upToDateWhen(t -> !stripClassicLibs.get().getDidWork());
            task.args(
                    stripClassicLibs.get().getOutput().get().getAsFile().getAbsolutePath(),
                    task.getOutput().get().getAsFile().getParentFile().getAbsolutePath()
            );
        });

        OutputtingJavaExec createFrg2SrcMappings = tasks.withType(OutputtingJavaExec.class).getByName("createFrg2SrcMappings", task -> {
            task.getOutputs().upToDateWhen(t ->
                validateChecksumUpdating(frgMappingsFile, frgChecksumFile) && validateChecksumUpdating(srcMappingsFile, srcChecksumFile)
            );
            task.args(
                "genMediatorMappings",
                frgMappingsFile.toAbsolutePath(),
                srcMappingsFile.toAbsolutePath(),
                "-o",
                task.getOutput().get().getAsFile().getAbsolutePath()
            );
        });

        tasks.withType(JavaExec.class).getByName("renamePatches", task -> task.args(
            patchesDir.toAbsolutePath(),
            createFrg2SrcMappings.getOutput().get().getAsFile().getAbsolutePath(),
            renamedPatchesDir.toAbsolutePath())
        );

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

            // Classic jars had their dependencies obfuscated, so we have to remap them, this is not the case for JarModDev, as there, the mappings already exist
            if(!project1.getExtensions().getByType(ClassicMCExt.class).getVersion().get().startsWith("in"))
                tasks.withType(Zip2ZipCopy.class).getByName("stripLibraries", t -> t.getIncludedPaths().add("**"));
            else tasks.withType(Zip2ZipCopy.class).getByName("stripClassicLibraries", t -> t.getIncludedPaths().add("**"));

            tasks.withType(Patcher.class).getByName("applyCompilerPatches", task -> task.getPatchDir().set((srcRemapping ? renamedPatchesDir : patchesDir).toFile()));

            remapJar.configure(task -> {
                if(!srcRemapping) task.dependsOn(genMappings);
                task.getOutputs().upToDateWhen(t -> srcRemapping ? validateChecksumUpdating(srcMappingsFile, srcChecksumFile) : validateChecksumUpdating(frgMappingsFile, frgChecksumFile));
                task.args(
                        "remap",
                        (genATs.get().getOutput().get().getAsFile().exists() ? applyATs.get().getOutput() : restoreMeta.getOutput()).get().getAsFile().getAbsolutePath(),
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
