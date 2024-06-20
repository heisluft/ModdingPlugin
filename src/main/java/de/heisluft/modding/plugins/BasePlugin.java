package de.heisluft.modding.plugins;

import de.heisluft.modding.extensions.ClassicMCExt;
import de.heisluft.modding.repo.MCRepo;
import de.heisluft.modding.repo.ResourceRepo;
import de.heisluft.modding.tasks.*;
import de.heisluft.modding.util.Util;
import net.minecraftforge.artifactural.base.artifact.SimpleArtifactIdentifier;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.heisluft.modding.extensions.ClassicMCExt.FERGIE;
import static de.heisluft.modding.extensions.ClassicMCExt.SOURCE;

/**
 * A plugin implementation providing shared tasks among plugins related to classic modding
 */
public abstract class BasePlugin implements Plugin<Project> {
    public static final String REPO_URL = "https://heisluft.de/maven/";

    /**
     * Generates an action configuring a toolchain to use the specified java language version.
     * @param version the java version to config
     * @return the computed action
     */
    private Action<JavaToolchainSpec> versionOf(int version) {
        return javaToolchainSpec -> javaToolchainSpec.getLanguageVersion().set(JavaLanguageVersion.of(version));
    }

    /**
     * this source set is generated by the plugin. Subclasses shall use this field instead of looking it up
     * as its name may change.
     */
    protected SourceSet mcSourceSet;
    /**
     * The location of the deobf tools jar
     */
    protected File deobfToolsJarFile;
    /**
     * @inheritDoc
     *
     * @implNote subclasses should call super first as it sets up all fields as well as configuring shared tasks
     */
    @Override
    public void apply(Project project) {
        File buildDir = project.getBuildDir();

        System.out.println("Auto-downloading DeobfTools...");
        File deobfToolsDir = new File(buildDir, "downloadDeobfTools");
        deobfToolsDir.mkdirs();
        try {
            MavenDownload.manualDownload(
                    REPO_URL,
                    new SimpleArtifactIdentifier("de.heisluft.deobf.tooling", "DeobfTools", "latest", "all", null),
                    deobfToolsJarFile = new File(deobfToolsDir, "DeobfTools.jar")
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("DONE");

        MCRepo.init(project.getGradle(), REPO_URL);
        // Java Plugin needs to be applied first as we want to configure it
        project.getPluginManager().apply(JavaPlugin.class);
        JavaPluginExtension javaExt = project.getExtensions().getByType(JavaPluginExtension.class);
        JavaToolchainService service = project.getExtensions().getByType(JavaToolchainService.class);
        // Use java 21, needed for modlauncher.
        javaExt.toolchain(versionOf(21));
        mcSourceSet = javaExt.getSourceSets().maybeCreate("mc");
        // We don't want things like modlauncher to be available to the mc source code
        // so implementation will not cover that
        project.getConfigurations().getByName("implementation").setExtendsFrom(Collections.singleton(project.getConfigurations().getByName("mcImplementation")));
        project.getTasks().withType(JavaCompile.class).forEach(javaCompile -> {
            javaCompile.getOptions().setEncoding("UTF-8");
            // We want to ensure basic binary compatability with mojang.
            // Sorry, no NIO, Diamonds or lambdas for us (at least in the mc sourceSet :D).
            if(javaCompile.getName().equals(mcSourceSet.getCompileJavaTaskName())) {
                // Java 8 is the only version to still be available and being able to compile java 5. hooray.
                javaCompile.getJavaCompiler().set(service.compilerFor(versionOf(8)));
                javaCompile.setTargetCompatibility("1.5");
                javaCompile.setSourceCompatibility("1.5");
            }
        });
        // Maven Central hosts log4j, asm, jansi and lwjgl
        project.getRepositories().mavenCentral();
        // heisluft.de hosts jopt-simple with auto-module-name and JOgg
        project.getRepositories().maven(repo -> repo.setUrl("https://heisluft.de/maven/"));
        // minecraftforge.net hosts bsl, sjh and modlauncher
        project.getRepositories().maven(repo -> repo.setUrl("https://maven.neoforged.net/"));
        DependencyHandler d = project.getDependencies();
        // LWJGL
        d.add("mcImplementation", "org.lwjgl.lwjgl:lwjgl:2.9.3");
        d.add("mcImplementation", "org.lwjgl.lwjgl:lwjgl_util:2.9.3");
        // ModLauncher and BSL
        d.add("implementation", "cpw.mods:modlauncher:11.0.2");
        d.add("implementation", "cpw.mods:bootstraplauncher:2.0.2");
        d.add("implementation", "cpw.mods:securejarhandler:3.0.7");
        // This jopt-simple version is patched to contain an Auto-Module-Name within its Manifest
        d.add("implementation", "net.sf.jopt-simple:jopt-simple:5.0.5");
        // For Log4j ANSI support within IntelliJ and on Windows
        d.add("runtimeOnly", "org.fusesource.jansi:jansi:2.4.0");
        // register the mcVersion extension
        project.getExtensions().create("classicMC", ClassicMCExt.class);

        // setup shared tasks
        TaskContainer tasks = project.getTasks();

        tasks.getByName("classes").dependsOn(tasks.getByName(mcSourceSet.getClassesTaskName()));

        TaskProvider<Zip2ZipCopy> stripLibraries = tasks.register("stripLibraries", Zip2ZipCopy.class, task -> {
            task.getIncludedPaths().add("net/minecraft/**");
            task.getIncludedPaths().add("a/**");
            task.getIncludedPaths().add("com/a/**");
        });

        TaskProvider<OutputtingJavaExec> restoreMeta = tasks.register("restoreMeta", OutputtingJavaExec.class, task -> {
            task.setOutputFilename("minecraft.jar");
            task.dependsOn(stripLibraries);
            task.classpath(deobfToolsJarFile);
            task.getMainClass().set("de.heisluft.deobf.tooling.binfix.BinFixer");
            task.getJavaLauncher().set(project.getExtensions().getByType(JavaToolchainService.class).launcherFor(v -> v.getLanguageVersion().set(JavaLanguageVersion.of(8))));
            task.args(stripLibraries.get().getOutput().getAsFile().get().getAbsolutePath(), task.getOutput().getAsFile().get().getAbsolutePath());
        });

        TaskProvider<RemapTask> remapJarFrg = tasks.register("remapJarFrg", RemapTask.class, task -> {
            task.classpath(deobfToolsJarFile);
            task.getInput().set(restoreMeta.get().getOutput());
            task.setOutputFilename("minecraft-mapped-fergie.jar");
        });

        //TODO: remapSrc and decomp should not be updated like this, cause doLast only runs if the task executed
        TaskProvider<ATApply> applyAts = tasks.register("applyAts", ATApply.class, task -> {
            task.classpath(deobfToolsJarFile);
            task.getInput().set(remapJarFrg.get().getOutput());
            // This cant be a lambda because Gradle will shit itself otherwise
            //noinspection Convert2Lambda
            task.doLast(new Action<Task>() {
                @Override
                public void execute(@Nonnull Task t) {
                    RegularFileProperty out = ((OutputtingJavaExec) t).getOutput();
                    if(!out.getAsFile().get().exists()) return;
                    TaskContainer tc = t.getProject().getTasks();
                    if(t.getProject().getExtensions().getByType(ClassicMCExt.class).getMappingType().equals(FERGIE))
                        tc.withType(Decomp.class).getByName("decompMC", task -> task.getInput().set(out));
                    else
                        tc.withType(RemapTask.class).getByName("remapJarSrc", task -> task.getInput().set(out));
                }
            });
        });

        TaskProvider<MavenDownload> downloadFernFlower = tasks.register("downloadFernFlower", MavenDownload.class, task -> {
            task.getMavenRepoUrl().set(REPO_URL);
            task.getGroupName().set("com.jetbrains");
            task.getArtifactName().set("FernFlower");
            task.getOutput().set(new File(project.getBuildDir(), task.getName() + "/fernflower.jar"));
        });

        TaskProvider<OutputtingJavaExec> createFrg2SrcMappings = tasks.register("createFrg2SrcMappings", OutputtingJavaExec.class, task -> {
            task.onlyIf(task1 -> project.getExtensions().getByType(ClassicMCExt.class).getMappingType().equals(SOURCE));
            task.classpath(deobfToolsJarFile);
            task.setOutputFilename("frg2src.frg");
            task.getMainClass().set("de.heisluft.deobf.tooling.Remapper");
        });

        TaskProvider<RemapTask> remapJarSrc = tasks.register("remapJarSrc", RemapTask.class, task -> {
            task.dependsOn(applyAts, createFrg2SrcMappings);
            task.onlyIf(task1 -> project.getExtensions().getByType(ClassicMCExt.class).getMappingType().equals(SOURCE));
            task.classpath(deobfToolsJarFile);
            task.getInput().set((applyAts.get().getATFile().getAsFile().get().exists() ? applyAts : remapJarFrg).get().getOutput());
            task.getMappings().set(createFrg2SrcMappings.get().getOutput());
            task.setOutputFilename("minecraft-mapped-src.jar");
        });

        TaskProvider<Decomp> decompMC = tasks.register("decompMC", Decomp.class, task -> {
            task.dependsOn(downloadFernFlower, applyAts);
            task.classpath(downloadFernFlower.get().getOutput());
            System.out.println(applyAts.get().getATFile().getAsFile().get().exists());
            task.getInput().set((applyAts.get().getATFile().getAsFile().get().exists() ? applyAts : remapJarFrg).get().getOutput());
        });

        TaskProvider<Extract> extractSrc = tasks.register("extractSrc", Extract.class, task -> task.getInput().set(decompMC.get().getOutput()));

        TaskProvider<JavaExec> renamePatches = tasks.register("renamePatches", JavaExec.class, task -> {
            task.getOutputs().upToDateWhen(t -> !createFrg2SrcMappings.get().getDidWork());
            task.dependsOn(createFrg2SrcMappings);
            task.classpath(deobfToolsJarFile);
            task.getMainClass().set("de.heisluft.deobf.tooling.SrcLevelRemapper");
        });

        TaskProvider<Patcher> applyCompilerPatches = tasks.register("applyCompilerPatches", Patcher.class, task -> {
            task.getInput().set(extractSrc.get().getOutput());
            // This cant be a lambda because Gradle will shit itself otherwise
            //noinspection Convert2Lambda
            task.doFirst(new Action<Task>() { // If work needs to be done, we have to first purge the output
                @Override
                public void execute(@Nonnull Task task) {
                    try {
                        Util.deleteContents(((Patcher)task).getOutput().getAsFile().get());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        });

        TaskProvider<Copy> copySrc = tasks.register("copySrc", Copy.class, task -> {
            task.dependsOn(applyCompilerPatches);
            task.into(mcSourceSet.getJava().getSrcDirs().iterator().next());
            task.from(applyCompilerPatches.get().getOutput());
            task.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
            task.onlyIf(t -> {
                Path mcsd = task.getDestinationDir().toPath();
                if(!Files.isDirectory(mcsd)) try {
                    Files.createDirectories(mcsd);
                } catch(IOException e) {
                    return false;
                }
                try(Stream<Path> s = Files.walk(mcsd)) {
                    return s.noneMatch(Files::isRegularFile);
                } catch (IOException exception) {
                    return false;
                }
            });
        });

        tasks.register("regenSrc", Copy.class, task -> {
            task.dependsOn(applyCompilerPatches);
            task.into(mcSourceSet.getJava().getSrcDirs().iterator().next());
            task.from(applyCompilerPatches.get().getOutput());
            task.getOutputs().upToDateWhen(task1 -> false);
            task.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
            // This cant be a lambda because Gradle will shit itself otherwise
            //noinspection Convert2Lambda
            task.doFirst(new Action<Task>() {
                @Override
                public void execute(@Nonnull Task t) {
                    try {
                        Util.deleteContents(task.getDestinationDir());
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }
            });
        });

        tasks.register("genPatches", Differ.class, task -> {
            task.onlyIf(t -> t.getProject().getExtensions().getByType(ClassicMCExt.class).getMappingType().equals(FERGIE));
            task.dependsOn(copySrc);
            task.getModifiedSrcDir().set(mcSourceSet.getJava().getSrcDirs().iterator().next());
        });

        TaskProvider<CPFileDecorator> makeCPFileP = tasks.register("makeCPFile", CPFileDecorator.class);

        TaskProvider<IdeaRunConfigMaker> genBSLRun = tasks.register("genBSLRun", IdeaRunConfigMaker.class, t -> {
            t.dependsOn(makeCPFileP);
            ListProperty<String> jvmArgs = t.getJvmArgs();
            t.getConfigName().set("runClient");
            t.getMainClassName().set("cpw.mods.bootstraplauncher.BootstrapLauncher");
            t.getWorkDir().set("$PROJECT_DIR$/run");
            jvmArgs.add("-Dlog4j.skipJansi=false");
            jvmArgs.add("-Dmccl.mcClasses.dir=" + mcSourceSet.getOutput().getClassesDirs().iterator().next().getAbsolutePath());
            jvmArgs.add("-DlegacyClassPath.file=" + makeCPFileP.get().getOutput().get().getAsFile().getAbsolutePath());
            jvmArgs.add("-DignoreList=bootstraplauncher,securejarhandler,asm,minecraft-assets,mc");
            jvmArgs.add("--add-modules ALL-MODULE-PATH");
            jvmArgs.add("--add-opens java.base/java.util.jar=cpw.mods.securejarhandler");
            jvmArgs.add("--add-opens java.base/java.lang.invoke=cpw.mods.securejarhandler");
            jvmArgs.add("--add-exports java.base/sun.security.util=cpw.mods.securejarhandler");
            jvmArgs.add("--add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming");
        });

        project.afterEvaluate(project1 -> {
            ResourceRepo.init(project1);

            ExtensionContainer ext = project1.getExtensions();
            String version = ext.getByType(ClassicMCExt.class).getVersion().get();
            stripLibraries.configure(t -> {
              try {
                t.getInput().set(MCRepo.getInstance().resolve("minecraft", version).toFile());
              } catch(IOException e) {
                throw new RuntimeException(e);
              }
            });

            DependencyHandler dh = project1.getDependencies();
            dh.add("mcImplementation", "com.mojang:minecraft-assets:" + version);
            // Sound dependencies. Jogg is only used from classic c0.0.16+ to c0.30,
            // however, classic versioning is a mess, so we include it in all classic versions
            // It is replaced with paulscode starting indev versions from 2010
            if(!version.startsWith("in")) dh.add("mcImplementation", "de.jarnbjo:j-ogg-mc:1.0.1");
            else if(!version.substring(version.indexOf('-') + 1).startsWith("2009")) {
                dh.add("mcImplementation", "com.paulscode:Paulscode-SoundSystem:1.0.1");
                dh.add("mcImplementation", "com.paulscode:CodecWav:1.0.1");
                dh.add("mcImplementation", "com.paulscode:CodecJOrbis:1.0.3");
                dh.add("mcImplementation", "com.paulscode:LibraryLWJGLOpenAL:1.0.1");
                dh.add("mcImplementation", "com.paulscode:LibraryJavaSound:1.0.1");
            }

            tasks.withType(Decomp.class).getByName("decompMC", task -> {
                boolean src = ext.getByType(ClassicMCExt.class).getMappingType().equals(SOURCE);
                if (src) task.getInput().set(remapJarSrc.get().getOutput());
            });

            tasks.withType(Patcher.class).getByName("applyCompilerPatches", task -> {
                if (ext.getByType(ClassicMCExt.class).getMappingType().equals(SOURCE)) task.dependsOn(renamePatches);
            });

            genBSLRun.configure(task -> {
                List<String> startModules = Arrays.asList("asm-", "bootstraplauncher-", "securejarhandler-");
                ResolvedConfiguration cfg = task.getProject().getConfigurations().getByName("runtimeClasspath").getResolvedConfiguration();
                task.getJvmArgs().add(cfg
                        .getFiles().stream()
                        .filter(f -> startModules.stream().anyMatch(f.getName()::startsWith))
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator, "-p ", "")));
                if(version.startsWith("in-2010")) {
                    task.getJvmArgs().add(cfg.getResolvedArtifacts().stream()
                        .filter(ra -> ra.toString().contains(" (com.paulscode:"))
                        .map(ResolvedArtifact::getFile).map(File::getName)
                        .collect(Collectors.joining(",","-DmergeModules=","")));
                }
                task.getAppArgs().add("--version=" + version);
            });
        });
    }
}
