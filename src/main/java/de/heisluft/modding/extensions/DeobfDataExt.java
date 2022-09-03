package de.heisluft.modding.extensions;

import org.gradle.api.file.DirectoryProperty;

//TODO: Evaluate if one property is worth a separate extension
public abstract class DeobfDataExt {
    public abstract DirectoryProperty getDataCopyDestinationDir();
}
