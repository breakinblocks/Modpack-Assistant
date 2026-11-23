package com.breakinblocks.modpackassistant.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MAClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_BARRIERS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("display");
        SHOW_BARRIERS = builder
                .comment("Draw barrier blocks as a see-through red cube at all times. Set in game with /mpa showBarriers on or off.")
                .define("show_barriers", false);
        builder.pop();

        SPEC = builder.build();
    }

    private MAClientConfig() {
    }
}
