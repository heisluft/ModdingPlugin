package de.heisluft.modding;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public abstract class DownloadTask extends DefaultTask {

  public DownloadTask() {
    getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("minecraft.jar")));
  }

  @OutputFile
  public abstract RegularFileProperty getOutput();

  @TaskAction
  public void test() throws IOException {
    String version = getMCVersion().get();
    URL url = new URL("https://heisluft.de/maven/com/mojang/minecraft/"+ version + "/minecraft-" + version + ".jar");
    try(FileOutputStream os = new FileOutputStream(getOutput().getAsFile().get()); InputStream is = url.openConnection().getInputStream()) {
      byte[] buf = new byte[1024];
      int read;
      while((read = is.read(buf)) != -1) os.write(buf, 0, read);
    }
  }

  @Input
  public abstract Property<String> getMCVersion();
}
