package de.heisluft.modding.plugins;

import de.heisluft.modding.Constants;
import de.heisluft.modding.Ext;
import de.heisluft.modding.repo.MCRepo;
import de.heisluft.modding.repo.ResourceRepo;
import de.heisluft.modding.tasks.*;
import net.minecraftforge.artifactural.base.artifact.SimpleArtifactIdentifier;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    protected Path ctorFixedMC;

    /**
     * @inheritDoc
     *
     * @implNote subclasses should call super first as it sets up all fields as well as configuring shared tasks
     */
    @Override
    public void apply(Project project) {
        System.out.println("Plugin version: " + Constants.VERSION);

        File buildDir = project.getBuildDir();
        //Need to init this early, some tasks seem to be eagerly evaluated. TODO: Find out why or ditch task structure altogether.
        ctorFixedMC = buildDir.toPath().resolve("restoreMeta/minecraft-ctor-fix.jar");

        System.out.println("Version independent setup tasks running: ");
        System.out.println("  downloadDeobfTools:");
        File deobfToolsDir = new File(buildDir, "downloadDeobfTools");
        File fernFlowerJarFile;
        deobfToolsDir.mkdirs();
        try {
            MavenDownload.manualDownload(
                    REPO_URL,
                    new SimpleArtifactIdentifier("de.heisluft.reveng", "RevEng", "latest", "all", null),
                    deobfToolsJarFile = new File(deobfToolsDir, "RevEng.jar")
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("    DONE \n  downloadFernFlower:");
        File ffDir = new File(buildDir, "downloadFernFlower");
        ffDir.mkdirs();
        try {
            MavenDownload.manualDownload(
                    REPO_URL,
                    new SimpleArtifactIdentifier("com.jetbrains", "FernFlower", "latest", null, null),
                    fernFlowerJarFile = new File(ffDir, "FernFlower.jar")
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("    DONE\nVersion independent setup tasks COMPLETE");

        MCRepo.init(project.getGradle(), REPO_URL);
        // Java Plugin needs to be applied first as we want to configure it
        project.getPluginManager().apply(JavaPlugin.class);
        JavaPluginExtension javaExt = project.getExtensions().getByType(JavaPluginExtension.class);
        JavaToolchainService service = project.getExtensions().getByType(JavaToolchainService.class);
        // Use java 16, needed for modlauncher.
        javaExt.toolchain(versionOf(16));
        mcSourceSet = javaExt.getSourceSets().maybeCreate("mc");
        // We dont want things like modlauncher to be available to the mc source code
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
        project.getRepositories().maven(repo -> repo.setUrl("https://maven.minecraftforge.net/"));
        DependencyHandler d = project.getDependencies();
        // MCs dependencies. it shall be noted that j-ogg is only used on classic c0.0.16+ and
        // is replaced with paulscode in later indev? versions.
        d.add("mcImplementation", "org.lwjgl.lwjgl:lwjgl:2.9.3");
        d.add("mcImplementation", "org.lwjgl.lwjgl:lwjgl_util:2.9.3");
        d.add("mcImplementation", "de.jarnbjo:j-ogg-mc:1.0.1");
        // ModLauncher and its dependencies
        d.add("implementation", "cpw.mods:modlauncher:10.0.8");
        d.add("implementation", "cpw.mods:bootstraplauncher:1.1.2");
        d.add("implementation", "cpw.mods:securejarhandler:2.1.4");
        // This jopt-simple version is patched to contain an Auto-Module-Name within its Manifest
        d.add("implementation", "net.sf.jopt-simple:jopt-simple:5.0.5");
        // Log4j, manually upgraded
        d.add("implementation", "org.apache.logging.log4j:log4j-core:2.18.0");
        // For Log4j ANSI support within IntelliJ and on Windows
        d.add("runtimeOnly", "org.fusesource.jansi:jansi:2.4.0");
        // register the mcVersion extension
        project.getExtensions().create("classicMC", Ext.class);

        // TODO: try to merge as many tasks from subprojects into base plugins
        // setup shared tasks
        TaskContainer tasks = project.getTasks();

        TaskProvider<OutputtingJavaExec> decompMC = tasks.register("decompMC", OutputtingJavaExec.class, task -> {
            task.setOutputFilename("minecraft.jar");
            task.classpath(fernFlowerJarFile);
            task.getMainClass().set("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler");
            task.setMaxHeapSize("4G");
            task.getJavaLauncher().set(project.getExtensions().getByType(JavaToolchainService.class).launcherFor(v -> v.getLanguageVersion().set(JavaLanguageVersion.of(11))));
        });

        tasks.register("extractSrc", Extract.class, task -> {
            task.dependsOn(decompMC);
            task.getInput().set(decompMC.get().getOutput());

            // This cant be a lambda because Gradle will shit itself otherwise
            //noinspection Convert2Lambda
            task.doFirst(new Action<Task>() { // If work needs to be done, we have to first purge the output
                @Override
                public void execute(@Nonnull Task task) {
                    Path out = ((Extract)task).getOutput().getAsFile().get().toPath();
                    try(Stream<Path> stream = Files.walk(out)) {
                        stream.sorted((o1, o2) -> o1.startsWith(o2) ? -1 : o2.startsWith(o1) ? 1 : 0).forEach(p -> {
                            if(out.equals(p)) return;
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
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

        tasks.register("genPatches", Differ.class, task -> {
            task.getModifiedSrcDir().set(mcSourceSet.getJava().getSrcDirs().iterator().next());
        });

        project.afterEvaluate(project1 -> {
            // TODO: find out why Resource repo cannot be initialized pre-config
            ResourceRepo.init(project1);

            ExtensionContainer ext = project1.getExtensions();
            String version = ext.getByType(Ext.class).getVersion().get();
            Provider<JavaLauncher> jCMD = ext.getByType(JavaToolchainService.class).launcherFor(ext.getByType(JavaPluginExtension.class).getToolchain());

            System.out.println("Version dependent setup tasks running: ");
            System.out.println("  Fetch MC jar:");
            Path mcJarPath;
            Path restoreMetaBase = project1.getBuildDir().toPath().resolve("restoreMeta");
            try {
                mcJarPath = MCRepo.getInstance().resolve("minecraft", version);
                Files.createDirectories(restoreMetaBase);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            System.out.println("    DONE\n  Resurrect Metadata:");
            Path temp = restoreMetaBase.resolve("minecraft-inners-restored.jar");
            ctorFixedMC = restoreMetaBase.resolve("minecraft-ctor-fix.jar").toAbsolutePath();
            if(Files.notExists(temp) || Files.notExists(ctorFixedMC)) System.out.println();
            if(Files.notExists(temp)) {
                launchProcess(jCMD, deobfToolsJarFile.getAbsolutePath(), "de.heisluft.reveng.nests.InnerClassDetector", mcJarPath, temp);
                System.out.println();
            }
            if(Files.notExists(ctorFixedMC)) {
                launchProcess(jCMD, deobfToolsJarFile.getAbsolutePath(), "de.heisluft.reveng.ConstructorFixer", temp, ctorFixedMC);
                System.out.println();
            }
            System.out.println("    DONE");
            project1.getDependencies()
                    .add("mcImplementation", "com.mojang:minecraft-assets:" + version);
            genBSLRun.configure(task -> {
                List<String> startModules = Arrays.asList("asm-", "bootstraplauncher-", "securejarhandler-");
                task.getJvmArgs().add(task.getProject().getConfigurations().getByName("runtimeClasspath").getResolvedConfiguration()
                        .getFiles().stream()
                        .filter(f -> startModules.stream().anyMatch(f.getName()::startsWith))
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator, "-p ", "")));
                task.getAppArgs().add("--version=" + version);
            });
        });
    }

    static void launchProcess(Provider<JavaLauncher> jExec, String cp, String main, Object... args) {
        try {
            List<String> cmd = new ArrayList<>(args.length + 4);
            cmd.add(jExec.get().getExecutablePath().getAsFile().getAbsolutePath());
            cmd.add("-cp");
            cmd.add(cp);
            cmd.add(main);
            for (Object arg : args) cmd.add(arg.toString());
            Process p = new ProcessBuilder(cmd).start();
            InputStream is = p.getInputStream();
            InputStream es = p.getErrorStream();
            byte[] buf = new byte[512];
            while (p.isAlive()) {
                int read = is.read(buf);
                if(read > 0) System.out.write(buf, 0, read);
                read = es.read(buf);
                if(read > 0) System.err.write(buf, 0, read);
            }
            int eV = p.waitFor();
            if(eV != 0) throw new RuntimeException("Command '" + String.join(" ", cmd) + "' finished with nonzero exit value " + eV);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
