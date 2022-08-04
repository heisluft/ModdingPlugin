package de.heisluft.modding.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.*;

public class MavenMetaUtil {

  private static final Map<String, Map<String, Set<String>>> VALID_CACHE = new HashMap<>();

  public static boolean versionExists(String repoUrl, String group, String name, String version) throws IOException {
    String base = group.replace('.', '/') + "/" + name + "/";
    VALID_CACHE.putIfAbsent(repoUrl, new HashMap<>());
    Map<String, Set<String>> cacheForRepo = VALID_CACHE.get(repoUrl);
    try {
      cacheForRepo.computeIfAbsent(base, s -> {
        try {
          return getVersions(parseXML(repoUrl + s + "maven-metadata.xml"));
        } catch (IOException | SAXException e) {
          throw new UncheckedIOException(new IOException("Invalid content in " + repoUrl + s + "maven-metadata.xml", e));
        }
      });
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
    return cacheForRepo.get(base).contains(version);
  }

  public static Document parseXML(String url) throws IOException, SAXException {
    try(InputStream is = new URL(url).openStream()) {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
    } catch (ParserConfigurationException impossible) {
      throw new AssertionError("The default doc builder is misconfigured per default???", impossible);
    }
  }

  public static Set<String> getVersions(Document document) throws IOException {
    NodeList versionsQuery = document.getElementsByTagName("versions");
    if(versionsQuery.getLength() == 0) throw new IOException("maven-metadata.xml misses <versions> tag");
    Set<String> versions = new HashSet<>();
    NodeList versionElements = versionsQuery.item(0).getChildNodes();
    for (int i = 0; i < versionElements.getLength(); i++) {
      Node node = versionElements.item(i);
      if (!(node instanceof Element)) continue;
      Element element = (Element) node;
      if (element.getTagName().equals("version")) versions.add(element.getTextContent());
    }
    return versions;
  }
}