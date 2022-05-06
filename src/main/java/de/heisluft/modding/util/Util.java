package de.heisluft.modding.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class Util {

  private static final Map<String, ?> PROPS = Collections.singletonMap("create", "true");

  public static FileSystem createFS(File at, boolean createFile) throws IOException {
    Path p = at.toPath().toAbsolutePath();
    if(!Files.isRegularFile(p) && createFile) Files.write(p, new byte[]{80, 75, 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
    return FileSystems.newFileSystem(URI.create("jar:file:/" + p.toString().replace('\\', '/')), PROPS);
  }

  public static Predicate<Path> parsePattern(String pattern) {
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
}
