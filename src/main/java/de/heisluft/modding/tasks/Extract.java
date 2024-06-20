package de.heisluft.modding.tasks;

import de.heisluft.modding.util.Util;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class Extract extends DefaultTask {

  private final List<String> includePatterns = new ArrayList<>();

  @InputFile
  public abstract RegularFileProperty getInput();

  @OutputDirectory
  public abstract DirectoryProperty getOutput();

  @Input
  public List<String> getIncludedPaths(){
    return includePatterns;
  }

  public Extract() {
    getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()));
  }

  @TaskAction
  public void doStuff() throws IOException {
    try {
      Util.deleteContents(getOutput().getAsFile().get());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Path outDir = getOutput().get().getAsFile().toPath();
    Set<Predicate<Path>> patterns = includePatterns.stream().map(Util::parsePattern).collect(Collectors.toSet());
    try(FileSystem fs = Util.createFS(getInput().get().getAsFile(), false)) {
      Files.walk(fs.getPath("/")).filter(path -> Files.isRegularFile(path) && patterns.isEmpty() || patterns.stream().anyMatch(p -> p.test(path))).forEach(path -> {
        Path resolved = outDir.resolve(path.toString().substring(1));
        try {
          Files.createDirectories(resolved.getParent());
          Files.copy(path, resolved);
        } catch(IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    } catch(UncheckedIOException ex) {
      throw ex.getCause();
    }
  }
}
