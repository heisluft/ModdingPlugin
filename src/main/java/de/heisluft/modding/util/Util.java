package de.heisluft.modding.util;

import org.gradle.api.invocation.Gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class Util {

  private static final Map<String, ?> PROPS = Collections.singletonMap("create", "true");

  public static final MessageDigest SHA_512;

  private static final char[] hexChars = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

  static {
    try {
      SHA_512 = MessageDigest.getInstance("SHA-512");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static String bytesToHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) builder.append(hexChars[(((int) b) & 0xff) >>> 4]).append(hexChars[b & 0xf]);
    return builder.toString();
  }

  public static FileSystem createFS(File at, boolean createFile) throws IOException {
    Path p = at.toPath();
    if(!Files.isRegularFile(p) && createFile) Files.write(p, new byte[]{80, 75, 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
    try {
      return FileSystems.newFileSystem(new URI("jar", p.toAbsolutePath().toUri().toString(), null), PROPS);
    } catch (URISyntaxException e) {
      throw new AssertionError("THIS SHOULD NEVER HAPPEN!", e);
    }
  }

  /**
   *
   * @param urlString the url to connect to
   * @param buf the buffer to read into
   * @return the number of remaining bytes within the stream, or -1 if the stream had fewer data available than the buffers
   *         capacity
   * @throws IOException if the url could not be connected to.
   */
  public static int readSized(String urlString, byte[] buf) throws IOException {
    try(InputStream is = new URL(urlString).openStream()) {
      if(buf.length != is.read(buf)) return -1;
      return is.available();
    }
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

  public static Path getCacheBase(Gradle gradle) {
    File gradleUserHomeDir = gradle.getGradleUserHomeDir();
    return Paths.get(gradleUserHomeDir.getPath(), "caches", "classic_modding");
  }

  public static Path getCache(Gradle gradle, String... tail) {
    Path cache = Paths.get(getCacheBase(gradle).toString(), tail);
    if(!Files.isDirectory(cache)) {
      if(Files.exists(cache)) throw new RuntimeException("Cache directory could not be created, " +
          "a regular file with the same name exists at '" + cache + "'");
      try {
        Files.createDirectories(cache);
      } catch (IOException e) {
        throw new RuntimeException("Cache directory at '" + cache + "' could not be created!", e);
      }
    }
    return cache;
  }
}
