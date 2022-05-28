package de.heisluft.modding;

import de.heisluft.modding.repo.ResourceRepo;
import de.heisluft.modding.tasks.*;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

public class ClassicModdingPlugin implements Plugin<Project> {

  public static final String REPO_URL = "https://heisluft.de/maven/";

  @Override
  public void apply(Project project) {
    System.out.println("ClassicModdingPlugin version " + Constants.VERSION + " initialized");

    // Java Plugin needs to be applied first as we want to configure it
    project.getPluginManager().apply(JavaPlugin.class);
    // Java 8 is the only version to still be available and being able to compile java 5. hooray.
    JavaPluginExtension javaExt = project.getExtensions().getByType(JavaPluginExtension.class);
    javaExt.toolchain(it -> {
      it.getLanguageVersion().set(JavaLanguageVersion.of(8));
      it.getVendor().set(JvmVendorSpec.ADOPTOPENJDK);
    });
    // We need to ensure binary compat with mojang. sorry, no NIO, Diamonds or lambdas for us.
    project.getTasks().withType(JavaCompile.class).forEach(javaCompile -> {
      javaCompile.getOptions().setEncoding("UTF-8");
      javaCompile.setTargetCompatibility("1.5");
      javaCompile.setSourceCompatibility("1.5");
    });
    // Maven Central hosts launchwrappers and mcs dependencies
    project.getRepositories().mavenCentral();
    // heisluft.de hosts the latest launchwrapper compiled for java 5 as well as jogg
    project.getRepositories().maven(repo -> repo.setUrl("https://heisluft.de/maven/"));
    DependencyHandler d = project.getDependencies();
    d.add("implementation", "net.minecraft:launchwrapper:1.12");
    d.add("implementation", "org.lwjgl.lwjgl:lwjgl:2.9.3");
    d.add("implementation", "org.lwjgl.lwjgl:lwjgl_util:2.9.3");
    d.add("implementation", "de.jarnbjo:j-ogg-mc:1.0.1");
    d.add("implementation", "de.heisluft.classiclaunch:launch:1.0.0");

    Ext ext = project.getExtensions().create("classicMC", Ext.class);

    TaskContainer tasks = project.getTasks();
    TaskProvider<MavenDownload> downloadDeobfTools = tasks.register("downloadDeobfTools", MavenDownload.class, task -> {
      task.getGroupName().set("de.heisluft.reveng");
      task.getArtifactName().set("RevEng");
      task.getClassifier().set("all");
      task.getMavenRepoUrl().set(REPO_URL);
    });
    TaskProvider<MavenDownload> downloadFernFlower = tasks.register("downloadFernFlower", MavenDownload.class, task -> {
      task.getGroupName().set("com.jetbrains");
      task.getArtifactName().set("FernFlower");
      task.getMavenRepoUrl().set(REPO_URL);
    });
    TaskProvider<MavenDownload> downloadMC = tasks.register("downloadMC", MavenDownload.class, task -> {
      task.getGroupName().set("com.mojang");
      task.getArtifactName().set("minecraft");
      task.getVersion().set(ext.getVersion());
      task.getMavenRepoUrl().set(REPO_URL);
    });
    TaskProvider<MavenDownload> downloadDeobfData = tasks.register("downloadDeobfData", MavenDownload.class, task -> {
      task.getGroupName().set("de.heisluft.deobf.data");
      task.getArtifactName().set(ext.getVersion());
      task.getExtension().set("zip");
      task.getMavenRepoUrl().set(REPO_URL);
    });
    TaskProvider<OutputtingJavaExec> fixConstructors = tasks.register("fixConstructors", OutputtingJavaExec.class, task -> {
      task.dependsOn(downloadMC, downloadDeobfTools);
      task.classpath(downloadDeobfTools.get().getOutput().get());
      task.setOutputFilename("minecraft.jar");
      task.getMainClass().set("de.heisluft.reveng.ConstructorFixer");
      task.args(
          downloadMC.get().getOutput().get().getAsFile().getAbsolutePath(),
          task.getOutput().get().getAsFile().getAbsolutePath()
      );
    });
    TaskProvider<OutputtingJavaExec> fixEnumSwitches = tasks.register("fixEnumSwitches", OutputtingJavaExec.class, task -> {
      task.dependsOn(fixConstructors);
      task.classpath(downloadDeobfTools.get().getOutput().get());
      task.setOutputFilename("minecraft.jar");
      task.getMainClass().set("de.heisluft.reveng.nests.EnumSwitchClassDetector");
      task.args(
          fixConstructors.get().getOutput().get().getAsFile().getAbsolutePath(),
          task.getOutput().get().getAsFile().getAbsolutePath()
      );
    });
    TaskProvider<Extract> extractData = tasks.register("extractData", Extract.class, task -> {
      task.dependsOn(downloadDeobfData);
      task.getInput().set(downloadDeobfData.get().getOutput());
      task.getIncludedPaths().addAll(Arrays.asList("fergie.frg", "at.cfg", "patches/*"));
    });
    TaskProvider<OutputtingJavaExec> remapJar = tasks.register("remapJar", OutputtingJavaExec.class, task -> {
      task.dependsOn(extractData, fixEnumSwitches);
      task.classpath(downloadDeobfTools.get().getOutput().get());
      task.setOutputFilename("minecraft.jar");
      task.getMainClass().set("de.heisluft.reveng.Remapper");
      task.args(
          "remap",
          fixEnumSwitches.get().getOutput().get().getAsFile().getAbsolutePath(),
          new File(extractData.get().getOutput().getAsFile().get(), "fergie.frg").getAbsolutePath(),
          "-o",
          task.getOutput().get().getAsFile().getAbsolutePath()
      );
    });
    TaskProvider<Zip2ZipCopy> stripLibs = tasks.register("stripLibraries", Zip2ZipCopy.class, task -> {
      task.dependsOn(remapJar);
      task.getInput().set(remapJar.get().getOutput());
      task.getIncludedPaths().addAll(Arrays.asList("util/**", "com/**"));
    });
    TaskProvider<OutputtingJavaExec> applyAts = tasks.register("applyAts", OutputtingJavaExec.class, task -> {
      task.dependsOn(stripLibs);
      task.classpath(downloadDeobfTools.get().getOutput().get());
      task.setOutputFilename("minecraft.jar");
      task.getMainClass().set("de.heisluft.reveng.at.ATApplicator");
      task.args(
          stripLibs.get().getOutput().getAsFile().get().getAbsolutePath(),
          new File(extractData.get().getOutput().getAsFile().get(), "at.cfg"),
          task.getOutput().getAsFile().get().getAbsolutePath()
      );
    });
    TaskProvider<OutputtingJavaExec> decompMC = tasks.register("decompMC", OutputtingJavaExec.class, task -> {
      task.dependsOn(applyAts, downloadFernFlower);
      task.setOutputFilename("minecraft.jar");
      task.classpath(downloadFernFlower.get().getOutput().get());
      task.getMainClass().set("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler");
      task.setMaxHeapSize("4G");
      task.args(
          applyAts.get().getOutput().get().getAsFile().getAbsolutePath(),
          task.getOutput().get().getAsFile().toPath().getParent().toAbsolutePath().toString()
      );
    });
    TaskProvider<Extract> extractSrc = tasks.register("extractSrc", Extract.class, task -> {
      task.dependsOn(decompMC);
      task.getInput().set(decompMC.get().getOutput());
    });
    TaskProvider<Patcher> applyCompilerPatches = tasks.register("applyCompilerPatches", Patcher.class, task -> {
      task.dependsOn(extractSrc);
      task.getPatchDir().set(extractData.get().getOutput().dir("patches"));
      task.getInput().set(extractSrc.get().getOutput());
    });
    TaskProvider<Copy> copySrc = tasks.register("copySrc", Copy.class, task -> {
      task.dependsOn(applyCompilerPatches);
      Set<File> srcDirs = javaExt.getSourceSets().getByName("main").getJava().getSrcDirs();
      task.into(srcDirs.iterator().next());
      task.from(applyCompilerPatches.get().getOutput());
      task.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
    });
    tasks.register("genPatches", Differ.class, task -> {
      task.dependsOn("applyCompilerPatches");
      task.getBackupSrcDir().set(applyCompilerPatches.get().getOutput());
      task.getModifiedSrcDir().set(copySrc.get().getDestinationDir());
    });
    project.afterEvaluate(project1 -> {
      ResourceRepo.init(project1);
      project.getDependencies().add("implementation", "com.mojang:minecraft-assets:" + ext.getVersion().get());
    });
  }
}