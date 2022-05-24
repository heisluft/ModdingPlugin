package de.heisluft.modding;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.StreamSupport;

public abstract class Patcher extends DefaultTask {

  @InputDirectory
  public abstract DirectoryProperty getInput();

  @InputDirectory
  public abstract DirectoryProperty getPatchDir();

  @OutputDirectory
  public abstract DirectoryProperty getOutput();

  public Patcher() {
    getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()));
  }

  @TaskAction
  public void doStuff() throws IOException {
    try {
      Map<String, Patch<String>> patches = StreamSupport.stream(
          getPatchDir().getAsFileTree().spliterator(), false).map(f -> {
        try {
          return Files.readAllLines(f.toPath());
        } catch(IOException e) {
          throw new UncheckedIOException(e);
        }
      }).collect(Collector.of(HashMap::new,
          (hashMap, strings) -> hashMap.put(extractPatchedPath(strings.get(0)), UnifiedDiffUtils.parseUnifiedDiff(strings)),
          (hashMap, hashMap2) -> {
            hashMap.putAll(hashMap2);
            return hashMap;
          }));
      Path inDirRoot = getInput().getAsFile().get().toPath();
      Path outDirRoot = getOutput().getAsFile().get().toPath();
      Files.walk(inDirRoot).filter(Files::isRegularFile).forEach(p -> {
        try {
          Path outPath = outDirRoot.resolve(inDirRoot.relativize(p));
          Files.createDirectories(outPath.getParent());
          Files.copy(p, outPath);
        } catch(IOException e) {
          throw new UncheckedIOException(e);
        }
      });
      patches.forEach((s, stringPatch) -> {
        try {
          Files.write(outDirRoot.resolve(s), DiffUtils.patch(Files.readAllLines(inDirRoot.resolve(s)), stringPatch));
        } catch(IOException e) {
          throw new UncheckedIOException(e);
        } catch(PatchFailedException e) {
          throw new RuntimeException(e);
        }
      });
    } catch(UncheckedIOException ex) {
      throw ex.getCause();
    }
  }

  private static String extractPatchedPath(String line) {
    int indexOfTab = line.indexOf('\t');
    return line.substring(4, indexOfTab == -1 ? line.length() : indexOfTab).replace("src/main/java/", "");
  }
}
