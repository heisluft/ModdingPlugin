package de.heisluft.modding;

import org.gradle.api.provider.Property;

public abstract class Ext {
  public abstract Property<String> getVersion();
  public abstract Property<String> getServerVersion();
}
