package com.breakinblocks.modpackassistant.gametest;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.analysis.BiomeSampler;
import com.breakinblocks.modpackassistant.analysis.LootContexts;
import com.breakinblocks.modpackassistant.analysis.LootSimulator;
import com.breakinblocks.modpackassistant.analysis.OreScan;
import com.breakinblocks.modpackassistant.analysis.RecipeConflictFinder;
import com.breakinblocks.modpackassistant.commands.items.ItemStrings;
import com.breakinblocks.modpackassistant.commands.items.OutputFormat;
import com.breakinblocks.modpackassistant.jobs.RegionGeometry;
import com.breakinblocks.modpackassistant.jobs.Run;
import com.breakinblocks.modpackassistant.jobs.RunScheduler;
import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.util.RomanNumerals;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class CoreGameTests {
    private CoreGameTests() {
    }

    static ServerPlayer fakePlayer(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos absolute = helper.absolutePos(relative);
        player.snapTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 180.0F, 0.0F);
        return player;
    }

    static CommandSourceStack source(ServerPlayer player) {
        return player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER).withSuppressedOutput();
    }

    static void run(GameTestHelper helper, CommandSourceStack source, String command) {
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
    }

    public static void romanNumerals(GameTestHelper helper) {
        Map<Integer, String> expected = Map.of(11, "XI", 40, "XL", 49, "XLIX", 90, "XC", 99, "XCIX", 100, "C", 149, "CXLIX", 200, "CC", 255, "CCLV");
        expected.forEach((value, numeral) -> helper.assertTrue(RomanNumerals.of(value).equals(numeral), value + " should be " + numeral + " but was " + RomanNumerals.of(value)));
        helper.succeed();
    }

    public static void csvEscaping(GameTestHelper helper) {
        String content = new CsvWriter().row("a,b", "say \"hi\"", 3).content();
        helper.assertTrue(content.trim().equals("\"a,b\",\"say \"\"hi\"\"\",3"), "CSV quoting was: " + content);
        helper.succeed();
    }

    public static void regionGeometry(GameTestHelper helper) {
        RegionGeometry region = new RegionGeometry(new ChunkPos(3, -4), 2);
        helper.assertTrue(region.span() == 5, "span should be 5");
        helper.assertTrue(region.chunkCount() == 25, "chunk count should be 25");
        helper.assertTrue(region.chunks().contains(new ChunkPos(1, -6)) && region.chunks().contains(new ChunkPos(5, -2)), "corners should be included");
        helper.succeed();
    }

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

    public static void giveStringRoundTrips(GameTestHelper helper) {
        var lookup = helper.getLevel().registryAccess();
        ItemStack plain = new ItemStack(Items.STONE, 3);
        helper.assertTrue(ItemStrings.giveString(plain, lookup).equals("minecraft:stone"), "plain stack should have no component block");

        ItemStack stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Round \"trip\""));
        stack.set(DataComponents.MAX_STACK_SIZE, 7);
        stack.remove(DataComponents.RARITY);
        String give = ItemStrings.giveString(stack, lookup);
        helper.assertTrue(give.startsWith("minecraft:diamond["), "give string should start with the item id: " + give);
        helper.assertTrue(give.contains("!minecraft:rarity"), "removed components should be marked: " + give);
        try {
            ItemInput parsed = new ItemParser(lookup).parse(new StringReader(give));
            ItemStack rebuilt = parsed.createItemStack(1);
            helper.assertTrue(ItemStack.isSameItemSameComponents(stack, rebuilt), "parsed stack differs, give string was: " + give);
        } catch (CommandSyntaxException e) {
            helper.assertTrue(false, "give string did not parse: " + e.getMessage() + " in " + give);
        }
        helper.succeed();
    }

    public static void oreScanCountsPlacedOres(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = ChunkPos.containing(helper.absolutePos(new BlockPos(1, 1, 1)));
        OreScan before = new OreScan(RegionGeometry.minY(level), RegionGeometry.maxY(level));
        before.scanChunk(level.getChunk(chunk.x(), chunk.z()), chunk);
        long existing = before.total();

        for (int i = 0; i < 5; i++) {
            helper.setBlock(new BlockPos(1, 1 + i, 1), Blocks.IRON_ORE);
        }
        OreScan after = new OreScan(RegionGeometry.minY(level), RegionGeometry.maxY(level));
        after.scanChunk(level.getChunk(chunk.x(), chunk.z()), chunk);
        helper.assertTrue(after.total() == existing + 5, "expected " + (existing + 5) + " ores, found " + after.total());
        boolean ironListed = after.ranked().stream().anyMatch(entry -> entry.block().equals(Identifier.withDefaultNamespace("iron_ore")) && entry.count() >= 5);
        helper.assertTrue(ironListed, "iron ore should be ranked with at least 5");
        helper.succeed();
    }

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

    public static void biomeSamplerLoadsNoChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(100_000, 0, 100_000);
        ChunkPos farChunk = ChunkPos.containing(far);
        helper.assertFalse(level.getChunkSource().hasChunk(farChunk.x(), farChunk.z()), "far chunk should start unloaded");
        BiomeSampler sampler = new BiomeSampler(level, far, 256, 16, 64);
        sampler.rows().forEach(sampler::sampleRow);
        helper.assertTrue(sampler.samples() == sampler.expectedSamples(), "sample count mismatch");
        helper.assertFalse(level.getChunkSource().hasChunk(farChunk.x(), farChunk.z()), "biome sampling must not load chunks");
        helper.succeed();
    }

    public static void recipeConflictsAndDuplicates(GameTestHelper helper) {
        List<Ingredient> stoneInputs = List.of(Ingredient.of(Items.STONE), Ingredient.of(Items.STONE));
        List<Ingredient> dirtInputs = List.of(Ingredient.of(Items.DIRT), Ingredient.of(Items.DIRT));
        RecipeHolder<?> a = shapeless("test_a", Items.DIAMOND, stoneInputs);
        RecipeHolder<?> b = shapeless("test_b", Items.EMERALD, stoneInputs);
        RecipeHolder<?> c = shapeless("test_c", Items.DIAMOND, stoneInputs);
        RecipeHolder<?> d = shapeless("test_d", Items.DIAMOND, dirtInputs);
        RecipeHolder<?> e = shapeless("test_e", Items.DIAMOND, dirtInputs);

        RecipeConflictFinder finder = new RecipeConflictFinder(helper.getLevel());
        finder.prepare(List.of(a, b, c, d, e), null);
        finder.buckets().forEach(finder::process);
        helper.assertTrue(finder.conflictCount() == 1, "expected 1 conflict group, got " + finder.conflictCount());
        helper.assertTrue(finder.duplicateCount() == 1, "expected 1 duplicate group, got " + finder.duplicateCount());
        helper.succeed();
    }

    private static RecipeHolder<ShapelessRecipe> shapeless(String name, net.minecraft.world.item.Item result, List<Ingredient> inputs) {
        ShapelessRecipe recipe = new ShapelessRecipe(
                new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                new ItemStackTemplate(result),
                inputs);
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, ModpackAssistant.id(name)), recipe);
    }

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
