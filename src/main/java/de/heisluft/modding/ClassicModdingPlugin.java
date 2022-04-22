package de.heisluft.modding;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import java.util.Arrays;

public class ClassicModdingPlugin implements Plugin<Project> {

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
      javaCompile.setTargetCompatibility("1.5");
      javaCompile.setSourceCompatibility("1.5");
    });
    project.getRepositories().mavenCentral();
    project.getRepositories().maven(repo -> repo.setUrl("https://heisluft.de/maven/"));
    project.getDependencies().add("implementation", "net.minecraft:launchwrapper:1.12");
    Ext ext = project.getExtensions().create("classicMC", Ext.class);

    TaskContainer tasks = project.getTasks();
    TaskProvider<DownloadTask> downloadMC = tasks.register("downloadMC", DownloadTask.class);
    downloadMC.configure(task -> task.getMCVersion().set(ext.getVersion()));
    tasks.register("extractResources", ExtractResourcesTask.class, task -> {
      task.dependsOn(downloadMC);
      task.getInput().set(downloadMC.get().getOutput());
      task.getIncludedPaths().addAll(Arrays.asList("**.png", "**.gif"));
      task.getOutputDir().set(project.file("src/main/resources/"));
    });
  }
}