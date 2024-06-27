package de.heisluft.modding.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;

public interface MinecraftVersion extends Comparable<MinecraftVersion> {

  static Comparator<Class<? extends MinecraftVersion>> COMPARATOR = (o1, o2) -> o1.equals(DatedVersion.class) ? o2.equals(DatedVersion.class) ? 0 : 1 : o2.equals(DatedVersion.class) ? -1 : 0;

  static MinecraftVersion of(String versionString) {
    if(versionString.startsWith("in")) return new DatedVersion(versionString);
    if(versionString.startsWith("c")) return new ClassicVersion(versionString);
    throw new IllegalArgumentException("Invalid version String '" + versionString + "'");
  }

  static final class ClassicVersion implements MinecraftVersion {
    private final int major;
    private final int minor;
    private final @Nullable String revision;


    private ClassicVersion(@NotNull String versionString) {
      if(versionString.length() < 5) throw new IllegalArgumentException("Invalid version String '" + versionString + "'");
      String[] split = versionString.substring(3).split("\\.", 2);
      int i;
      try {
        i = Integer.parseInt(split[0]);
      } catch(NumberFormatException e) {
        if(split.length != 1) throw new IllegalArgumentException("Invalid version String '" + versionString + "'");
        int suffixIndex = split[0].indexOf('_');
        for(int j = 0; j < split[0].length(); j++) {
          char cur = split[0].charAt(j);
          if(cur < '0' && (suffixIndex < 0 || j < suffixIndex)) throw new IllegalArgumentException("Invalid version String '" + versionString + "'");
          if(cur > '9' && suffixIndex < 0 && (cur != 'a' || j != split[0].length() - 1)) throw new IllegalArgumentException("Invalid version String '" + versionString + "'");
        }
        i = Integer.parseInt(split[0].substring(0, suffixIndex < 0 ? split[0].length() - 1 : suffixIndex));
      }
      major = i;
      if(split.length == 1) minor = 0;
      else {
        try {
          i = Integer.parseInt(split[1]);
        } catch(NumberFormatException e) {
          int suffixIndex = split[1].indexOf('_');
          boolean hasA = false;
          int bound = suffixIndex >= 0 ? suffixIndex : split[1].length();
          for(int j = 0; j < bound; j++) {
            char cur = split[1].charAt(j);
            if(cur < '0') throw new IllegalArgumentException("Invalid version String '" + versionString + "'");
            if(cur > '9') {
              if(cur != 'a' || j != bound - 1) throw new IllegalArgumentException("Invalid version String '" + versionString + "'");
              hasA = true;
            }
          }
          i = Integer.parseInt(split[1].substring(0, bound - (hasA ? 1 : 0)));
        }
        minor = i;
      }
      int suffixIndex = split[split.length - 1].indexOf('_');
      revision = suffixIndex < 0 ? null : split[split.length - 1].substring(suffixIndex + 1);
    }

    @Override
    public int compareTo(@NotNull MinecraftVersion o) {
      int phaseComparison = COMPARATOR.compare(this.getClass(), o.getClass());
      if(phaseComparison != 0) return phaseComparison;
      ClassicVersion v = (ClassicVersion) o;
      if(major - v.major != 0) return major - v.major;
      if(minor - v.minor != 0) return minor - v.minor;
      return revision == null ? v.revision != null ? -1 : 0 : v.revision == null ? 1 : revision.compareTo(v.revision);
    }

    @Override
    public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      ClassicVersion that = (ClassicVersion) o;
      return major == that.major && minor == that.minor && Objects.equals(revision, that.revision);
    }

    @Override
    public int hashCode() {
      return Objects.hash(major, minor, revision);
    }

    @Override
    public String toString() {
      return "ClassicVersion{major=" + major + ", minor=" + minor + ", revision='" + revision + "'}";
    }
  }

  static final class DatedVersion implements MinecraftVersion {
    private final boolean indev;
    private final @NotNull String date;
    private final @Nullable String revision;

    private DatedVersion(@NotNull String versionString) {
      String[] parts = versionString.split("-", 3);
      if(parts.length < 2) throw new IllegalArgumentException("Invalid version String '" + versionString + "'");
      indev = "in".equals(parts[0]);
      if(!indev && !"inf".equals(parts[0])) throw new IllegalArgumentException("Invalid version String '" + versionString + "'");
      this.date = parts[1];
      this.revision = parts.length == 3 ? parts[2] : null;
    }

    @Override
    public int compareTo(@NotNull MinecraftVersion o) {
      int phaseComparison = COMPARATOR.compare(this.getClass(), o.getClass());
      if(phaseComparison != 0) return phaseComparison;
      DatedVersion v = (DatedVersion) o;
      if(indev && !v.indev) return -1;
      if(!indev && v.indev) return 1;
      int dateComp = date.compareTo(v.date);
      return dateComp != 0 ? dateComp : revision == null ? v.revision == null ? 0 : -1 : v.revision == null ? 1 : revision.compareTo(v.revision);
    }

    @Override
    public boolean equals(Object o) {
      if(this == o) return true;
      if(o == null || getClass() != o.getClass()) return false;
      DatedVersion that = (DatedVersion) o;
      return indev == that.indev && Objects.equals(date, that.date) && Objects.equals(revision, that.revision);
    }

    @Override
    public int hashCode() {
      return Objects.hash(indev, date, revision);
    }

    @Override
    public String toString() {
      return "DatedVersion{phase='" + (indev ? "indev" : "infdef") + "', date='" + date + "', revision='" + revision + "'}";
    }
  }
}
