package com.saunhardy.createringtoncurrency;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HIDE_MOJANG_LOGO;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Loading screen settings").push("loadingScreen");

        HIDE_MOJANG_LOGO = BUILDER
                .comment("If true, NeoForge will NOT add the Mojang logo to the early loading window while resources load")
                .define("hideMojangLogo", false);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private ClientConfig() {}

    public static boolean hideMojangLogo() {
        return SPEC.isLoaded() && HIDE_MOJANG_LOGO.get();
    }
}
