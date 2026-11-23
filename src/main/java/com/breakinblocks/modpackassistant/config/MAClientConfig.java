package com.breakinblocks.modpackassistant.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class MAClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SHOW_BARRIERS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

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
