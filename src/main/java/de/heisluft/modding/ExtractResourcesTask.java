package de.heisluft.modding;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class ExtractResourcesTask extends DefaultTask {
  private static final Map<String, ?> PROPS = Collections.singletonMap("create", "true");
  private final List<String> includePatterns = new ArrayList<>();

  @InputFile
  public abstract RegularFileProperty getInput();

  @OutputDirectory
  public abstract DirectoryProperty getOutputDir();

  @Input
  public List<String> getIncludedPaths(){
    return includePatterns;
  }

  private Predicate<Path> parsePattern(String pattern) {
    char[] chars = pattern.toCharArray();
    StringBuilder builder = new StringBuilder("^\\/");
    for(int i = 0; i < pattern.length(); i++) {
      char c = chars[i];
      switch(c) {
        case '*': if(i < chars.length - 1 && chars[i + 1] == '*') {
          builder.append(".*"); i++;
        } else builder.append("[^/]*");
        break;
        case '/': builder.append("\\/"); break;
        case '.': builder.append('\\');
        default: builder.append(c);
      }
    }
    Pattern result = Pattern.compile(builder.append('$').toString());
    return p -> result.matcher(p.toString()).matches();
  }

  @TaskAction
  public void doAction() throws IOException {
    Path out = getOutputDir().getAsFile().get().toPath();
    Set<Predicate<Path>> patterns = includePatterns.stream().map(this::parsePattern).collect(Collectors.toSet());
    URI uri = URI.create("jar:file:/" + getInput().get().getAsFile().getAbsolutePath().replace('\\', '/'));
    try(FileSystem fs = FileSystems.newFileSystem(uri, PROPS)) {
      Files.walk(fs.getPath("/")).filter(path -> patterns.stream().anyMatch(p -> p.test(path))).forEach(path -> {
        Path dest = out.resolve(path.toString().substring(1)); // Works because all paths within a zip start with '/'
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
}
