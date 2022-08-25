package de.heisluft.modding.repo;

import de.heisluft.modding.util.MavenMetaUtil;
import de.heisluft.modding.util.Util;
import org.apache.log4j.Logger;
import org.gradle.api.invocation.Gradle;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MCRepo {

  private static MCRepo instance;
  private final String repoURL;
  private final Path cacheDir;
  private static final Logger LOGGER = Logger.getLogger("MCRepo");

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

  public Path resolve(String name, String version) throws IOException {
    if(!"minecraft".equals(name) && !"minecraft-server".equals(name))
      throw new IllegalArgumentException("Repo only resolves minecraft jars");
    boolean repoReachable, versionExists;
    try {
      versionExists = MavenMetaUtil.versionExists(repoURL, "com.mojang" , name, version);
      repoReachable = true;
    } catch (IOException e) {
      versionExists = false;
      repoReachable = false;
      LOGGER.warn("Could not fetch versions online. Disabling checksum checking and download of missing versions");
      e.printStackTrace();
    }

    String fName = name + "-" + version + ".jar";
    Path targetFile = cacheDir.resolve(fName);
    boolean fileExistsLocally = Files.isRegularFile(targetFile);

    if(!repoReachable) {
      if (!fileExistsLocally) throw new FileNotFoundException(targetFile.toString());
      else return targetFile;
    }

    if(!versionExists && fileExistsLocally) {
      LOGGER.warn("version " + version + " does not exist in repo. It cannot be verified");
      return targetFile;
    }

    String targetFileURL = repoURL + "com/mojang/" + name + "/"+ version + "/" + fName;
    String expHash;
     {
      byte[] hashBuf = new byte[128];
      Util.readSized(targetFileURL + ".sha512", hashBuf);
      expHash = new String(hashBuf);
    }
    if(Files.isRegularFile(targetFile)) {
      String compHash = Util.bytesToHex(Util.SHA_512.digest(Files.readAllBytes(targetFile)));
      if(!compHash.equals(expHash)) {
        LOGGER.warn("warning: checksum mismatch for file " + targetFile.toAbsolutePath() + ", expected: " + expHash + ", computed: " + compHash);
      }
      return targetFile;
    }
    try(InputStream is = new URL(targetFileURL).openStream(); OutputStream os = Files.newOutputStream(targetFile)) {
      byte[] buf = new byte[8192];
      int read;
      while ((read = is.read(buf)) != -1) {
        Util.SHA_512.update(buf, 0, read);
        os.write(buf, 0, read);
      }
    }
    String compHash = Util.bytesToHex(Util.SHA_512.digest());
    if(!compHash.equals(expHash)) {
      LOGGER.warn("warning: checksum mismatch for file " + targetFile.toAbsolutePath() + ", expected: " + expHash + ", computed: " + compHash);
    }
    return targetFile;
  }
}
