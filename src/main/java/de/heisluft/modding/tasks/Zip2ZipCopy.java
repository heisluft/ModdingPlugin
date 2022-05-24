package de.heisluft.modding.tasks;

import de.heisluft.modding.util.Util;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
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

public abstract class Zip2ZipCopy extends DefaultTask {

  private final List<String> includePatterns = new ArrayList<>();

  @InputFile
  public abstract RegularFileProperty getInput();

  @OutputFile
  public abstract RegularFileProperty getOutput();

  @Input
  public List<String> getIncludedPaths(){
    return includePatterns;
  }

  public Zip2ZipCopy() {
    getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("output.jar")));
  }

  public static void doExec(File in, File out, List<String> includePatterns) throws IOException {
    Set<Predicate<Path>> patterns = includePatterns.stream().map(Util::parsePattern).collect(Collectors.toSet());
    try(FileSystem inFs = Util.createFS(in, false); FileSystem outFs = Util.createFS(out, true)) {
      Files.walk(inFs.getPath("/")).filter(path -> patterns.isEmpty() || patterns.stream().anyMatch(p -> p.test(path))).forEach(path -> {
        Path dest = outFs.getPath(path.toString());
        try {
          Files.createDirectories(dest.getParent());
          Files.copy(path, dest);
        } catch(IOException ex){
          throw new UncheckedIOException(ex);
        }
      });
    } catch(UncheckedIOException e) {
      throw e.getCause();
    }
  }

  @TaskAction
  public void doAction() throws IOException {
    doExec(getInput().getAsFile().get(), getOutput().getAsFile().get(), includePatterns);
  }
}
