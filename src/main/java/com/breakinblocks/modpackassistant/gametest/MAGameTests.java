package com.breakinblocks.modpackassistant.gametest;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

@EventBusSubscriber(modid = ModpackAssistant.MOD_ID)
public final class MAGameTests {
    private static final Identifier EMPTY_STRUCTURE = ModpackAssistant.id("empty");

    public static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_INSTANCE_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, ModpackAssistant.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends GameTestInstance>, MapCodec<DirectGameTestInstance>> DIRECT_TYPE =
            TEST_INSTANCE_TYPES.register("direct", () -> DirectGameTestInstance.CODEC);

    private MAGameTests() {
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        register(event, "roman_numerals", CoreGameTests::romanNumerals, 100);
        register(event, "csv_escaping", CoreGameTests::csvEscaping, 100);
        register(event, "region_geometry", CoreGameTests::regionGeometry, 100);
        register(event, "copy_formats_carry_count", CoreGameTests::copyFormatsCarryCount, 100);
        register(event, "give_string_round_trips", CoreGameTests::giveStringRoundTrips, 100);
        register(event, "ore_scan_counts_placed_ores", CoreGameTests::oreScanCountsPlacedOres, 100);
        register(event, "run_scheduler_refuses_overlap_and_cancels", CoreGameTests::runSchedulerRefusesOverlapAndCancels, 200);
        register(event, "biome_sampler_loads_no_chunks", CoreGameTests::biomeSamplerLoadsNoChunks, 100);
        register(event, "recipe_conflicts_and_duplicates", CoreGameTests::recipeConflictsAndDuplicates, 100);
        register(event, "loot_simulator_matches_declared_chance", CoreGameTests::lootSimulatorMatchesDeclaredChance, 100);

        register(event, "clear_remove_predicate_protects_bedrock", CommandGameTests::clearRemovePredicateProtectsBedrock, 200);
        register(event, "clear_keep_ores_and_modded_leaves_ores", CommandGameTests::clearKeepOresAndModdedLeavesOres, 200);
        register(event, "drain_removes_connected_fluid_within_radius", CommandGameTests::drainRemovesConnectedFluidWithinRadius, 200);
        register(event, "kill_all_skips_protected_and_players", CommandGameTests::killAllSkipsProtectedAndPlayers, 100);
        register(event, "tpd_moves_vehicle_with_passenger", CommandGameTests::tpdMovesVehicleWithPassenger, 200000);
        register(event, "scan_ores_writes_report", CommandGameTests::scanOresWritesReport, 200);
        register(event, "locate_block_lists_nearest_first_and_writes_report", CommandGameTests::locateBlockListsNearestFirstAndWritesReport, 200);
        register(event, "radius_above_limit_is_refused", CommandGameTests::radiusAboveLimitIsRefused, 100);
        register(event, "alias_and_lowercase_literals_work", CommandGameTests::aliasAndLowercaseLiteralsWork, 100);
        register(event, "structure_loot_places_and_clears", CommandGameTests::structureLootPlacesAndClears, 200);
    }

    private static void register(RegisterGameTestsEvent event, String name, Consumer<GameTestHelper> function, int timeoutTicks) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                ModpackAssistant.id(name),
                new TestEnvironmentDefinition.AllOf());
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, EMPTY_STRUCTURE, timeoutTicks, 0, true);
        event.registerTest(ModpackAssistant.id(name), new DirectGameTestInstance(name, function, data));
    }
}
