package de.heisluft.modding.tasks;

import de.heisluft.modding.util.ArtifactIdentifier;
import de.heisluft.modding.util.MavenMetaUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public abstract class MavenDownload extends DefaultTask {

  @Input
  public abstract Property<String> getGroupName();

  @Input
  public abstract Property<String> getArtifactName();

  @Input
  public abstract Property<String> getMavenRepoUrl();

  @Input
  public abstract Property<String> getVersion();

  @Input
  public abstract Property<String> getExtension();

  @Input
  @Optional
  public abstract Property<String> getClassifier();

  public MavenDownload() {
    getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("output.jar")));
    getVersion().convention("latest");
    getExtension().convention("jar");
  }

  @OutputFile
  public abstract RegularFileProperty getOutput();

  public static void manualDownload(String repoUrl, ArtifactIdentifier id, File output) throws IOException {
    String c = id.classifier == null ? "" : "-" + id.classifier;
    String ext = id.extension;
    doExec(repoUrl, id.groupId, id.artifactId, id.version, c, ext == null ? "jar" : ext, output);
  }

  private static void doExec(String repoUrl, String group, String name, String versionRaw, String classifier, String extension, File outputFile) throws IOException {
    String version, expandedVersion;
    String baseUrl = repoUrl + (repoUrl.endsWith("/") ? "" : "/") + group.replace('.', '/') + "/" + name + "/";
    try(InputStream is = new URL(baseUrl + "maven-metadata.xml").openStream()) {
      Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
      switch (versionRaw) {
        case "latest":
        case "release":
          NodeList query = document.getElementsByTagName(versionRaw);
          if(query.getLength() == 0) throw new FileNotFoundException("Found no data for version '" + versionRaw + "' of artifact '" + name + "' in maven repo at " + repoUrl);
          version = query.item(0).getTextContent();
          break;
        default: version = versionRaw;
      }
      NodeList versionsQuery = document.getElementsByTagName("versions");
      if(versionsQuery.getLength() == 0) throw new FileNotFoundException("maven-metadata.xml does not list any versions. This is an error on the repo side! Report to maintainers of " + repoUrl);
      if(!MavenMetaUtil.getVersions(document).contains(version)) throw new FileNotFoundException("Found no data for version '" + version + "' of artifact '" + name + "' in maven repo at " + repoUrl);
    } catch(SAXException | ParserConfigurationException ex) {
      throw new RuntimeException(ex);
    }
    if(version.endsWith("-SNAPSHOT")) {
      try(InputStream is = new URL(baseUrl + version + "/maven-metadata.xml").openStream()) {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        NodeList query = document.getElementsByTagName("timestamp");
        NodeList query2 = document.getElementsByTagName("buildNumber");
        if(query.getLength() != 1 || query2.getLength() != 1) throw new FileNotFoundException("snapshot maven-metadata.xml is malformed");
        expandedVersion = version.replace("SNAPSHOT", query.item(0).getTextContent() + "-" + query2.item(0).getTextContent());
      } catch(SAXException | ParserConfigurationException ex) {
        throw new RuntimeException(ex);
      }
    } else expandedVersion = version;
    URL url = new URL(baseUrl + version + "/" + name  + "-" + expandedVersion + classifier + "." + extension);
    try(FileOutputStream os = new FileOutputStream(outputFile); InputStream is = url.openConnection().getInputStream()) {
      byte[] buf = new byte[1024];
      int read;
      while((read = is.read(buf)) != -1) os.write(buf, 0, read);
    }
  }

  @TaskAction
  public void test() throws IOException {
    String repoUrl = getMavenRepoUrl().get();
    String group = getGroupName().get();
    String name = getArtifactName().get();
    String classifier = getClassifier().isPresent() ? "-" + getClassifier().get() : "";
    String extension = getExtension().get();
    String reqVersion = getVersion().get();
    doExec(repoUrl, group, name, reqVersion, classifier, extension, getOutput().get().getAsFile());
  }
}
