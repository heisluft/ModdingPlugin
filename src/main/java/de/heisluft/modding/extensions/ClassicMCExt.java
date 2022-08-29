package de.heisluft.modding.extensions;

import org.gradle.api.provider.Property;

public abstract class ClassicMCExt {
  public abstract Property<String> getVersion();
  public abstract Property<String> getServerVersion();
}
