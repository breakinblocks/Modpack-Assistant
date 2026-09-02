package com.breakinblocks.modpackassistant.gametest;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.analysis.BiomeSampler;
import com.breakinblocks.modpackassistant.analysis.LootContexts;
import com.breakinblocks.modpackassistant.analysis.LootSimulator;
import com.breakinblocks.modpackassistant.analysis.OreScan;
import com.breakinblocks.modpackassistant.analysis.RecipeConflictFinder;
import com.breakinblocks.modpackassistant.commands.items.OutputFormat;
import com.breakinblocks.modpackassistant.jobs.RegionGeometry;
import com.breakinblocks.modpackassistant.jobs.Run;
import com.breakinblocks.modpackassistant.jobs.RunScheduler;
import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.util.RomanNumerals;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(ModpackAssistant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreGameTests {
    static final String EMPTY = "empty";

    static ServerPlayer fakePlayer(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 180.0F, 0.0F);
        return player;
    }

    static CommandSourceStack source(ServerPlayer player) {
        return player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
    }

    static void run(GameTestHelper helper, CommandSourceStack source, String command) {
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
    }

    @GameTest(batch = "core_romanNumerals", template = EMPTY)
    public static void romanNumerals(GameTestHelper helper) {
        Map<Integer, String> expected = Map.of(11, "XI", 40, "XL", 49, "XLIX", 90, "XC", 99, "XCIX", 100, "C", 149, "CXLIX", 200, "CC", 255, "CCLV");
        expected.forEach((value, numeral) -> helper.assertTrue(RomanNumerals.of(value).equals(numeral), value + " should be " + numeral + " but was " + RomanNumerals.of(value)));
        helper.succeed();
    }

    @GameTest(batch = "core_csvEscaping", template = EMPTY)
    public static void csvEscaping(GameTestHelper helper) {
        String content = new CsvWriter().row("a,b", "say \"hi\"", 3).content();
        helper.assertTrue(content.trim().equals("\"a,b\",\"say \"\"hi\"\"\",3"), "CSV quoting was: " + content);
        helper.succeed();
    }

    @GameTest(batch = "core_regionGeometry", template = EMPTY)
    public static void regionGeometry(GameTestHelper helper) {
        RegionGeometry region = new RegionGeometry(new ChunkPos(3, -4), 2);
        helper.assertTrue(region.span() == 5, "span should be 5");
        helper.assertTrue(region.chunkCount() == 25, "chunk count should be 25");
        helper.assertTrue(region.chunks().contains(new ChunkPos(1, -6)) && region.chunks().contains(new ChunkPos(5, -2)), "corners should be included");
        helper.succeed();
    }

    @GameTest(batch = "core_copyFormats", template = EMPTY)
    public static void copyFormatsCarryCount(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.STONE, 17);
        var lookup = helper.getLevel().registryAccess();
        for (OutputFormat format : OutputFormat.values()) {
            String output = format.write(List.of(stack, new ItemStack(Items.DIRT, 17)), lookup);
            helper.assertTrue(output.contains("17"), format.formatName() + " lost the count: " + output);
            if (format != OutputFormat.CSV) {
                helper.assertFalse(output.trim().endsWith(",") || output.contains(",\n]") || output.contains(",\n  }\n]"), format.formatName() + " has a trailing separator: " + output);
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "core_oreScan", template = EMPTY)
    public static void oreScanCountsPlacedOres(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(helper.absolutePos(new BlockPos(1, 1, 1)));
        OreScan before = new OreScan(RegionGeometry.minY(level), RegionGeometry.maxY(level));
        before.scanChunk(level.getChunk(chunk.x, chunk.z), chunk);
        long existing = before.total();

        for (int i = 0; i < 5; i++) {
            helper.setBlock(new BlockPos(1, 1 + i, 1), Blocks.IRON_ORE);
        }
        OreScan after = new OreScan(RegionGeometry.minY(level), RegionGeometry.maxY(level));
        after.scanChunk(level.getChunk(chunk.x, chunk.z), chunk);
        helper.assertTrue(after.total() == existing + 5, "expected " + (existing + 5) + " ores, found " + after.total());
        boolean ironListed = after.ranked().stream().anyMatch(entry -> entry.block().equals(ResourceLocation.withDefaultNamespace("iron_ore")) && entry.count() >= 5);
        helper.assertTrue(ironListed, "iron ore should be ranked with at least 5");
        helper.succeed();
    }

    @GameTest(batch = "core_runScheduler", template = EMPTY, timeoutTicks = 200)
    public static void runSchedulerRefusesOverlapAndCancels(GameTestHelper helper) {
        helper.assertFalse(RunScheduler.isBusy(), "another run is active before the test started");
        ServerPlayer player = fakePlayer(helper, new BlockPos(8, 1, 8));
        CommandSourceStack source = source(player);
        AtomicInteger executed = new AtomicInteger();
        Run first = new Run(source, "test run", helper.getLevel().dimension());
        for (int i = 0; i < 50; i++) {
            first.job(executed::incrementAndGet);
        }
        helper.assertTrue(RunScheduler.tryStart(first), "first run should start");
        Run second = new Run(source, "second run", helper.getLevel().dimension()).job(() -> {});
        helper.assertFalse(RunScheduler.tryStart(second), "second run should be refused while the first is active");
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(executed.get() > 0 && executed.get() < 50, "run should be in progress, executed " + executed.get());
            helper.assertTrue(RunScheduler.cancel(source), "cancel should succeed");
            int atCancel = executed.get();
            helper.runAfterDelay(12, () -> {
                helper.assertTrue(executed.get() == atCancel, "no jobs should run after cancel");
                helper.assertFalse(RunScheduler.isBusy(), "scheduler should be idle after cancel");
                helper.succeed();
            });
        });
    }

    @GameTest(batch = "core_biomeSamplerNoChunks", template = EMPTY)
    public static void biomeSamplerLoadsNoChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(100_000, 0, 100_000);
        ChunkPos farChunk = new ChunkPos(far);
        helper.assertFalse(level.getChunkSource().hasChunk(farChunk.x, farChunk.z), "far chunk should start unloaded");
        BiomeSampler sampler = new BiomeSampler(level, far, 256, 16, 64);
        sampler.rows().forEach(sampler::sampleRow);
        helper.assertTrue(sampler.samples() == sampler.expectedSamples(), "sample count mismatch");
        helper.assertFalse(level.getChunkSource().hasChunk(farChunk.x, farChunk.z), "biome sampling must not load chunks");
        helper.succeed();
    }

    @GameTest(batch = "core_recipeConflicts", template = EMPTY)
    public static void recipeConflictsAndDuplicates(GameTestHelper helper) {
        var lookup = helper.getLevel().registryAccess();
        NonNullList<Ingredient> inputs = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE), Ingredient.of(Items.STONE));
        RecipeHolder<?> a = new RecipeHolder<>(ModpackAssistant.id("test_a"), new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND), inputs));
        RecipeHolder<?> b = new RecipeHolder<>(ModpackAssistant.id("test_b"), new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.EMERALD), inputs));
        RecipeHolder<?> c = new RecipeHolder<>(ModpackAssistant.id("test_c"), new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND), inputs));
        NonNullList<Ingredient> other = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIRT), Ingredient.of(Items.DIRT));
        RecipeHolder<?> d = new RecipeHolder<>(ModpackAssistant.id("test_d"), new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND), other));
        RecipeHolder<?> e = new RecipeHolder<>(ModpackAssistant.id("test_e"), new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND), other));

        RecipeConflictFinder finder = new RecipeConflictFinder(lookup);
        finder.prepare(List.of(a, b, c, d, e), null);
        finder.buckets().forEach(finder::process);
        helper.assertTrue(finder.conflictCount() == 1, "expected 1 conflict group, got " + finder.conflictCount());
        helper.assertTrue(finder.duplicateCount() == 1, "expected 1 duplicate group, got " + finder.duplicateCount());
        helper.succeed();
    }

    @GameTest(batch = "core_lootSimulator", template = EMPTY)
    public static void lootSimulatorMatchesDeclaredChance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = LootTable.lootTable()
                .withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.DIAMOND).when(LootItemRandomChanceCondition.randomChance(0.5F))))
                .build();
        ServerPlayer player = fakePlayer(helper, new BlockPos(8, 1, 8));
        LootContexts.Built built = LootContexts.build(level, player.blockPosition(), player, 0.0F, LootContextParamSets.EMPTY);
        helper.assertTrue(built.ok(), "empty param set should build");
        LootSimulator simulator = new LootSimulator(table, built.params(), level.registryAccess(), 20_000);
        while (simulator.rolled() < simulator.iterations()) {
            simulator.rollBatch();
        }
        double chance = simulator.ranked().get(0).dropChance(simulator.rolled());
        helper.assertTrue(Math.abs(chance - 50.0D) < 2.0D, "expected about 50% but got " + chance);
        helper.assertTrue(Math.abs(simulator.emptyPercent() - 50.0D) < 2.0D, "expected about 50% empty rolls but got " + simulator.emptyPercent());

        LootContexts.Built block = LootContexts.build(level, player.blockPosition(), player, 0.0F, LootContextParamSets.BLOCK);
        helper.assertFalse(block.ok(), "block param set should report missing parameters");
        helper.assertTrue(block.missing().contains("minecraft:block_state") && block.missing().contains("minecraft:tool"), "missing list was " + block.missing());
        helper.succeed();
    }
}
