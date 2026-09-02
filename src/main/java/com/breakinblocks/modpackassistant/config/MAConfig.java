package com.breakinblocks.modpackassistant.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MAConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue ITEM_INSPECTION_PERMISSION;
    public static final ModConfigSpec.IntValue MAX_CLEAR_RADIUS;
    public static final ModConfigSpec.IntValue MAX_SCAN_RADIUS;
    public static final ModConfigSpec.IntValue MAX_DRAIN_BLOCKS;
    public static final ModConfigSpec.IntValue MAX_LOOT_ITERATIONS;
    public static final ModConfigSpec.IntValue MAX_SIMULATED_TICKS;
    public static final ModConfigSpec.IntValue MAX_BIOME_SAMPLES;
    public static final ModConfigSpec.IntValue MAX_BIOME_SAMPLE_RADIUS;
    public static final ModConfigSpec.ConfigValue<String> REPORT_DIRECTORY;
    public static final ModConfigSpec.IntValue JOB_INTERVAL_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("permissions");
        ITEM_INSPECTION_PERMISSION = builder
                .comment("Permission level required for the item print, hand and copy commands. 0 lets every player use them.")
                .defineInRange("item_inspection_permission", 0, 0, 4);
        builder.pop();

        builder.push("limits");
        MAX_CLEAR_RADIUS = builder
                .comment("Largest chunk radius accepted by the region clear command.")
                .defineInRange("max_clear_radius", 16, 0, 64);
        MAX_SCAN_RADIUS = builder
                .comment("Largest chunk radius accepted by the ore scan and mining simulation commands.")
                .defineInRange("max_scan_radius", 32, 0, 64);
        MAX_DRAIN_BLOCKS = builder
                .comment("Largest number of fluid blocks a single drain may remove.")
                .defineInRange("max_drain_blocks", 250_000, 1, 4_000_000);
        MAX_LOOT_ITERATIONS = builder
                .comment("Largest iteration count accepted by the loot simulator.")
                .defineInRange("max_loot_iterations", 1_000_000, 1, 10_000_000);
        MAX_SIMULATED_TICKS = builder
                .comment("Largest tick count accepted by the spawn simulator.")
                .defineInRange("max_simulated_ticks", 432_000, 1, 4_320_000);
        MAX_BIOME_SAMPLES = builder
                .comment("Largest sample count accepted by the biome mapper.")
                .defineInRange("max_biome_samples", 1_000_000, 100, 10_000_000);
        MAX_BIOME_SAMPLE_RADIUS = builder
                .comment("Largest block radius accepted by the biome mapper.")
                .defineInRange("max_biome_sample_radius", 10_000, 16, 100_000);
        builder.pop();

        builder.push("reports");
        REPORT_DIRECTORY = builder
                .comment("Report output directory, relative to the game directory.")
                .define("report_directory", "logs/modpackassistant");
        builder.pop();

        builder.push("scheduler");
        JOB_INTERVAL_TICKS = builder
                .comment("Server ticks between scheduled jobs of a long-running operation.")
                .defineInRange("job_interval_ticks", 5, 1, 100);
        builder.pop();

        SPEC = builder.build();
    }

    private MAConfig() {
    }

    public static int itemInspectionPermission() {
        return ITEM_INSPECTION_PERMISSION.get();
    }

    public static int maxClearRadius() {
        return MAX_CLEAR_RADIUS.get();
    }

    public static int maxScanRadius() {
        return MAX_SCAN_RADIUS.get();
    }

    public static int maxDrainBlocks() {
        return MAX_DRAIN_BLOCKS.get();
    }

    public static int maxLootIterations() {
        return MAX_LOOT_ITERATIONS.get();
    }

    public static int maxSimulatedTicks() {
        return MAX_SIMULATED_TICKS.get();
    }

    public static int maxBiomeSamples() {
        return MAX_BIOME_SAMPLES.get();
    }

    public static int maxBiomeSampleRadius() {
        return MAX_BIOME_SAMPLE_RADIUS.get();
    }

    public static String reportDirectory() {
        return REPORT_DIRECTORY.get();
    }

    public static int jobIntervalTicks() {
        return JOB_INTERVAL_TICKS.get();
    }
}
