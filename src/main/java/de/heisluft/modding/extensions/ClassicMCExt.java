package de.heisluft.modding.extensions;

import org.gradle.api.provider.Property;

import javax.annotation.Nonnull;

public abstract class ClassicMCExt {
  public static final String FERGIE = "fergie", SOURCE = "source";

  public abstract Property<String> getVersion();
  public abstract Property<String> getServerVersion();

  @Nonnull
  private String mappingType = FERGIE;

  @Nonnull
  public String getMappingType() {
    return mappingType;
  }

  public void setMappingType(@Nonnull String mappingType) {
    this.mappingType = mappingType;
  }
}
