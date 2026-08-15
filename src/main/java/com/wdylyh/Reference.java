package com.wdylyh;

import net.fabricmc.loader.api.FabricLoader;

public class Reference {
    public static final String MOD_ID = "reignrender";
    public static final String MOD_NAME = "ReignRender";

    /**
     * The mod version is read from the fabric.mod.json metadata (driven by the
     * Gradle project version), so it can never drift from the built artifact.
     */
    public static final String MOD_VERSION = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
}