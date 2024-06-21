package de.heisluft.modding.plugins;

import de.heisluft.modding.extensions.ClassicMCExt;
import de.heisluft.modding.tasks.ATApply;
import de.heisluft.modding.tasks.Differ;
import de.heisluft.modding.tasks.Extract;
import de.heisluft.modding.tasks.OutputtingJavaExec;
import de.heisluft.modding.tasks.Patcher;
import de.heisluft.modding.tasks.RemapTask;
import de.heisluft.modding.tasks.Zip2ZipCopy;
import de.heisluft.modding.util.Util;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;
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
        Path renamedPatchesDir = project.getLayout().getBuildDirectory().getAsFile().get().toPath().resolve("renamePatches");

        OutputtingJavaExec restoreMeta = tasks.withType(OutputtingJavaExec.class).getByName("restoreMeta", task -> {
            List<String> args = task.getArgs();
            args.add(project.getLayout().getBuildDirectory().file(task.getName() + "/mappings.frg").get().getAsFile().getAbsolutePath());
            task.setArgs(args);
        });

        try {
            Files.createDirectories(patchesDir);
            Files.createDirectories(renamedPatchesDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        TaskProvider<OutputtingJavaExec> genMappings = tasks.register("genMappings", OutputtingJavaExec.class, task -> {
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("mappings-generated.frg");
            task.getMainClass().set("de.heisluft.deobf.tooling.Remapper");
            task.args(
                    "map",
                    restoreMeta.getOutput().get().getAsFile().getAbsolutePath(),
                    task.getOutput().get().getAsFile().getAbsolutePath(),
                    "-s",
                    project.getLayout().getBuildDirectory().file(restoreMeta.getName() + "/mappings.frg").get().getAsFile().getAbsolutePath()
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
                        if (!Files.isRegularFile(atFile) && task.getOutput().get().getAsFile().exists()) {
                            Files.copy(task.getOutput().get().getAsFile().toPath(), atFile);
                            Files.write(atChecksumFile, Util.SHA_512.digest(Files.readAllBytes(atFile)));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        });

      tasks.withType(RemapTask.class).getByName("remapJarFrg", task -> {
        task.dependsOn(genMappings);
        task.getMappings().set(frgMappingsFile.toFile());
      });

      tasks.withType(ATApply.class).getByName("applyAts", task -> {
            task.dependsOn(genATs);
            task.getOutputs().upToDateWhen(t -> validateChecksumUpdating(atFile, atChecksumFile) && !tasks.getByName("remapJarFrg").getDidWork());
            task.getATFile().set(atFile.toFile());
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
            // TODO: add stripClassicLibraries back
            //else tasks.withType(Zip2ZipCopy.class).getByName("stripClassicLibraries", t -> t.getIncludedPaths().add("**"));

            tasks.withType(Patcher.class).getByName("applyCompilerPatches", task -> task.getPatchDir().set((srcRemapping ? renamedPatchesDir : patchesDir).toFile()));
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
