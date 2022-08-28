package de.heisluft.modding.tasks;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public abstract class Differ extends DefaultTask {

  @InputDirectory
  public abstract DirectoryProperty getBackupSrcDir();

  @InputDirectory
  public abstract DirectoryProperty getModifiedSrcDir();

  @OutputDirectory
  public abstract DirectoryProperty getPatchDir();

  public Differ() {
    getPatchDir().convention(getProject().getLayout().getBuildDirectory().dir(getName()));
  }

  @TaskAction
  public void doStuff() throws IOException {
    Path origSrc = getBackupSrcDir().getAsFile().get().toPath();
    Path modSrc = getModifiedSrcDir().getAsFile().get().toPath();
    Path patches = getPatchDir().getAsFile().get().toPath();
    try(Stream<Path> ps = Files.walk(origSrc)) {
      ps.filter(Files::isRegularFile).forEach(p -> {
        try {
          Path rel = origSrc.relativize(p);
          List<String> origLines = Files.readAllLines(p);
          List<String> patchLines = UnifiedDiffUtils.generateUnifiedDiff(rel.toString(), "patches/" + rel, origLines,
              DiffUtils.diff(origLines, Files.readAllLines(modSrc.resolve(rel))),
              3
          );
          if(patchLines.isEmpty()) return;
          Files.write(patches.resolve(rel.getFileName().toString().replace(".java", ".patch")), patchLines);
        } catch(IOException ex) {
          throw new UncheckedIOException(ex);
        }
      });
    } catch(UncheckedIOException e) {
      throw e.getCause();
    }
  }
}
