package de.heisluft.modding;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public abstract class MavenDownloadTask extends DefaultTask {

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

  public MavenDownloadTask() {
    getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(dir -> dir.file("output.jar")));
    getVersion().convention("latest");
    getExtension().convention("jar");
  }

  @OutputFile
  public abstract RegularFileProperty getOutput();

  @TaskAction
  public void test() throws IOException, ParserConfigurationException, SAXException {
    String repoUrl = getMavenRepoUrl().get();
    if(!repoUrl.endsWith("/")) repoUrl += "/";
    String group = getGroupName().get().replace('.', '/');
    String name = getArtifactName().get();
    String classifier = getClassifier().isPresent() ? "-" + getClassifier().get() : "";
    String extension = getExtension().get();
    String baseUrl = repoUrl + group + "/" + name + "/";
    String reqVersion = getVersion().get();
    String version;
    try(InputStream is = new URL(baseUrl + "maven-metadata.xml").openStream()) {
      Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
      switch (reqVersion) {
        case "latest":
        case "release":
          NodeList query = document.getElementsByTagName(reqVersion);
          if(query.getLength() == 0) throw new FileNotFoundException("Found no data for version '" + reqVersion + "' of artifact '" + getGroupName().get() + "' in maven repo at " + repoUrl);
          version = query.item(0).getTextContent();
          break;
        default: version = reqVersion;
      }
      NodeList versionsQuery = document.getElementsByTagName("versions");
      if(versionsQuery.getLength() == 0) System.err.println("maven-metadata.xml does not list any versions. This is an error on the repo side! Report to maintainers of " + repoUrl);
      else {
        Element versionsElement = (Element) versionsQuery.item(0);
        NodeList versionElements = versionsElement.getChildNodes();
        boolean found = false;
        for (int i = 0; i < versionElements.getLength(); i++) {
          Node node = versionElements.item(i);
          if(!(node instanceof Element)) continue;
          Element element = (Element) node;
          if(!element.getTagName().equals("version")) continue; // We will not nag about it rn, it is not our job to validate such stuff
          if(element.getTextContent().equals(version)) {
            found = true;
            break;
          }
        }
        if(!found) throw new FileNotFoundException("Found no data for version '" + version + "' of artifact '" + getGroupName().get() + "' in maven repo at " + repoUrl);
      }
    }
    URL url = new URL(baseUrl + version + "/" + name  + "-" + version + classifier + "." + extension);
    try(FileOutputStream os = new FileOutputStream(getOutput().getAsFile().get()); InputStream is = url.openConnection().getInputStream()) {
      byte[] buf = new byte[1024];
      int read;
      while((read = is.read(buf)) != -1) os.write(buf, 0, read);
    }
  }
}
