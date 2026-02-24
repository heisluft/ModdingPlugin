package de.heisluft.modding.extensions;

import org.gradle.api.provider.Property;

import javax.annotation.Nonnull;

public abstract class ClassicMCExt {
  public static final String FERGIE = "fergie", SOURCE = "source";

  public abstract Property<String> getVersion();
  public abstract Property<String> getServerVersion();
  public abstract Property<String> getMappingType();

  public ClassicMCExt() {
    getMappingType().convention(FERGIE);
  }
}
