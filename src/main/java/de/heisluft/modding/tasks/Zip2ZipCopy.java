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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    Files.copy(in.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
    try(FileSystem outFs = Util.createFS(out, true); Stream<Path> stream = Files.walk(outFs.getPath("/"))) {
      stream.sorted(Comparator.reverseOrder()).filter(path -> !path.toString().equals("/") && (patterns.isEmpty() || patterns.stream().noneMatch(p -> p.test(path)))).forEach(path -> {
        try {
          if(Files.isRegularFile(path)) {
            System.out.println("deleting " + path);
            Files.delete(path);
            return;
          }
          if(!Files.list(path).findAny().isPresent()) {
            System.out.println("deleting " + path);
            Files.delete(path);
          }
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    } catch(UncheckedIOException e) {
      throw new IOException(e.getCause());
    }
  }

  @TaskAction
  public void doAction() throws IOException {
    doExec(getInput().getAsFile().get(), getOutput().getAsFile().get(), includePatterns);
  }
}
