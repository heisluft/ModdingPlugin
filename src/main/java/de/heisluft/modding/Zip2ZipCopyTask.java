package de.heisluft.modding;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
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

public abstract class Zip2ZipCopyTask extends DefaultTask {
  private static final Map<String, ?> PROPS = Collections.singletonMap("create", "true");
  private final List<String> includePatterns = new ArrayList<>();

  @InputFile
  public abstract RegularFileProperty getInput();

  @OutputFile
  public abstract RegularFileProperty getOutput();

  @Input
  public List<String> getIncludedPaths(){
    return includePatterns;
  }

  private final FileSystem createFS(File at, boolean createFile) throws IOException {
    Path p = at.toPath().toAbsolutePath();
    if(!Files.isRegularFile(p) && createFile) Files.write(p, new byte[]{80, 75, 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
    return FileSystems.newFileSystem(URI.create("jar:file:/" + p.toString().replace('\\', '/')), PROPS);
  }

  public Zip2ZipCopyTask() {
    getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("output.jar")));
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
        case '/':
        case '\\':
        case '[':
        case ']':
        case '(':
        case ')':
        case '{':
        case '}':
        case '|':
        case '?':
        case '+':
        case '$':
        case '^':
        case '.': builder.append('\\');
        default: builder.append(c);
      }
    }
    Pattern result = Pattern.compile(builder.append('$').toString());
    return p -> result.matcher(p.toString()).matches();
  }

  @TaskAction
  public void doAction() throws IOException {
    File out = getOutput().getAsFile().get();
    File in = getInput().getAsFile().get();
    Set<Predicate<Path>> patterns = includePatterns.stream().map(this::parsePattern).collect(Collectors.toSet());
    try(FileSystem inFs = createFS(in, false); FileSystem outFs = createFS(out, true)) {
      Files.walk(inFs.getPath("/")).filter(path -> patterns.stream().anyMatch(p -> p.test(path))).forEach(path -> {
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
}
