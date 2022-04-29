package de.heisluft.modding;

import org.gradle.api.DefaultTask;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public abstract class MavenJarDownloadTask extends DefaultTask {

  @Input
  public abstract Property<String> getArtifactLocation();

  @Input
  public abstract Property<String> getMavenRepoUrl();

  @Input
  @Optional
  public abstract Property<String> getClassifier();

  public MavenJarDownloadTask() {
    getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("dep.jar")));
  }

  @OutputFile
  public abstract RegularFileProperty getOutput();

  @TaskAction
  public void test() throws IOException {
    String repoUrl = getMavenRepoUrl().get();
    if(!repoUrl.endsWith("/")) repoUrl += "/";
    String[] depNotation = getArtifactLocation().get().split(":");
    if(depNotation.length != 2) throw new ProjectConfigurationException("artifactLocation must contain exactly one colon", new IllegalArgumentException());
    String group = depNotation[0].replace('.', '/');
    String name = depNotation[1];
    String version = getVersion().get();
    String classifier = getClassifier().isPresent() ? "-" + getClassifier().get() : "";
    URL url = new URL(repoUrl + group + "/" + name + "/" + version + "/" + name  + "-" + version + classifier + ".jar");
    System.out.println(url);
    try(FileOutputStream os = new FileOutputStream(getOutput().getAsFile().get()); InputStream is = url.openConnection().getInputStream()) {
      byte[] buf = new byte[1024];
      int read;
      while((read = is.read(buf)) != -1) os.write(buf, 0, read);
    }
  }

  @Input
  public abstract Property<String> getVersion();
}
