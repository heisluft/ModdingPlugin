package de.heisluft.modding.test;

import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static de.heisluft.modding.util.IdentifierUtil.parseIdentifier;
import static org.junit.jupiter.api.Assertions.*;

public class IdentifierTest {
  
  private void assertIAEOfMessage(String message, Executable e) {
    assertEquals(message, assertThrows(IllegalArgumentException.class, e).getMessage());
  }

  @Test
  public void shouldParseCorrectIdentifiers() {
    ArtifactIdentifier id1 = parseIdentifier("de.98zzg:ajjj:7.9.0-pre3+7:all@zip");
    assertEquals("de.98zzg", id1.getGroup());
    assertEquals("ajjj", id1.getName());
    assertEquals("7.9.0-pre3+7", id1.getVersion());
    assertEquals("all", id1.getClassifier());
    assertEquals("zip", id1.getExtension());
    id1 = parseIdentifier("de.98zzg:ajjj:7.9.0-pre3+7:all");
    assertEquals("de.98zzg", id1.getGroup());
    assertEquals("ajjj", id1.getName());
    assertEquals("7.9.0-pre3+7", id1.getVersion());
    assertEquals("all", id1.getClassifier());
    assertEquals("jar", id1.getExtension());
    id1 = parseIdentifier("de.98zzg:ajjj:7.9.0-pre3+7@zip");
    assertEquals("de.98zzg", id1.getGroup());
    assertEquals("ajjj", id1.getName());
    assertEquals("7.9.0-pre3+7", id1.getVersion());
    assertNull(id1.getClassifier());
    assertEquals("zip", id1.getExtension());
    id1 = parseIdentifier("de.98zzg:ajjj:7.9.0-pre3+7");
    assertEquals("de.98zzg", id1.getGroup());
    assertEquals("ajjj", id1.getName());
    assertEquals("7.9.0-pre3+7", id1.getVersion());
    assertNull(id1.getClassifier());
    assertEquals("jar", id1.getExtension());
    id1 = parseIdentifier("de.98zzg:ajjj");
    assertEquals("de.98zzg", id1.getGroup());
    assertEquals("ajjj", id1.getName());
    assertEquals("latest", id1.getVersion());
    assertNull(id1.getClassifier());
    assertEquals("jar", id1.getExtension());
  }

  @Test
  public void shouldErrorOnBadInput() {
    assertIAEOfMessage("GroupId must not be empty", () -> parseIdentifier(""));
    assertIAEOfMessage("GroupId must not be empty", () -> parseIdentifier(":"));
    assertIAEOfMessage("GroupId must not be empty", () -> parseIdentifier("::"));
    assertIAEOfMessage("ArtifactName must not be empty", () -> parseIdentifier("a"));
    assertIAEOfMessage("ArtifactName must not be empty", () -> parseIdentifier("a:"));
    assertIAEOfMessage("ArtifactName must not be empty", () -> parseIdentifier("a::"));
    assertIAEOfMessage("Version must not be empty if specified", () -> parseIdentifier("a:a:"));
    assertIAEOfMessage("Version must not be empty if specified", () -> parseIdentifier("a:a::"));
    assertIAEOfMessage("Version must not be empty if specified", () -> parseIdentifier("a:a:@"));
    assertIAEOfMessage("Classifier must not be empty if specified", () -> parseIdentifier("a:a:1:"));
    assertIAEOfMessage("Classifier must not be empty if specified", () -> parseIdentifier("a:a:1:@"));
    assertIAEOfMessage("Illegal char ':' in classifier or extension", () -> parseIdentifier("a:a:1@:"));
  }
}