package de.heisluft.modding.extensions;

import org.gradle.api.file.DirectoryProperty;

public abstract class DeobfDataExt {

    private int developmentPhase = 0;

    public int getDevelopmentPhase() {
        return developmentPhase;
    }

    public void setDevelopmentPhase(int developmentPhase) {
        this.developmentPhase = developmentPhase;
    }

    public abstract DirectoryProperty getDataCopyDestinationDir();
}
