package de.heisluft.modding.repo;

import de.heisluft.modding.util.Util;
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import org.gradle.api.invocation.Gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class MCRepo {

  private static final MessageDigest SHA_512;
  private static MCRepo instance;
  private final String repoURL;
  private final Path cacheDir;

  static {
    try {
      SHA_512 = MessageDigest.getInstance("SHA-512");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private MCRepo(Path cacheDir, String repoURL) throws IOException {
    this.repoURL = repoURL;
    this.cacheDir = cacheDir;
  }

  public static void init(Gradle gradle, String repoURL) {
    try {
      instance = new MCRepo(Util.getCache(gradle, "mc_repo"), repoURL);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static MCRepo getInstance() {
    if(instance == null) throw new IllegalStateException("Repo has not been initialized. Call #init(Gradle) first");
    return instance;
  }

  public File resolve(ArtifactIdentifier identifier) throws IOException {
    String group = identifier.getGroup();
    if(!"com.mojang".equals(group)) throw new IllegalArgumentException("Repo resolves only mojangs jars");
    String name = identifier.getName();
    if(!"minecraft".equals(name) && !"minecraft-server".equals(name))
      throw new IllegalArgumentException("Repo only resolves minecraft jars");
    String version = identifier.getVersion();
    String fName = name + "-" + version + ".jar";
    Path targetFile = cacheDir.resolve(fName);
    String targetFileURL = repoURL + group.replace('.', '/') + "/" + name + "/"+ version + "/" + fName;
    byte[] expHash = new byte[128];
    Util.readSized(targetFileURL + ".sha512", expHash);
    System.out.println(new String(expHash));
    if(Files.isRegularFile(targetFile)) {
      byte[] compHash = SHA_512.digest(Files.readAllBytes(targetFile));
      System.out.println(compHash.length);
      System.out.println(expHash.length);
      if(!Arrays.equals(compHash, expHash)){
        System.out.println("warning: checksum mismatch for file " + targetFile.toAbsolutePath());
        System.out.println("expected: " + new String(expHash) + ", computed: " + new String(expHash));
      }
    }
    return targetFile.toFile();
  }
}
