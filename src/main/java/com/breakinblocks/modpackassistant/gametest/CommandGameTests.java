package com.breakinblocks.modpackassistant.gametest;

import com.breakinblocks.modpackassistant.analysis.BlockLocator;
import com.breakinblocks.modpackassistant.data.TestLootPlacements;
import com.breakinblocks.modpackassistant.jobs.RunScheduler;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class CommandGameTests {
    private CommandGameTests() {
    }

    private static final int RUN_WAIT = 40;

    private static CommandSourceStack sourceAt(GameTestHelper helper, BlockPos relative) {
        return CoreGameTests.source(CoreGameTests.fakePlayer(helper, relative));
    }

    private static void requireIdle(GameTestHelper helper) {
        helper.assertFalse(RunScheduler.isBusy(), "another run is active before the test started");
    }

    private static long countReports(ReportWriter.Family family) {
        Path directory = ReportWriter.directory(family);
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.count();
        } catch (IOException e) {
            return -1;
        }
    }

    public static void clearRemovePredicateProtectsBedrock(GameTestHelper helper) {
        requireIdle(helper);
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.BEDROCK);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(4, 3, 2), Blocks.STONE);
        CommandSourceStack source = sourceAt(helper, new BlockPos(4, 5, 2));
        CoreGameTests.run(helper, source, "ma clear 0 remove #minecraft:base_stone_overworld");
        helper.runAfterDelay(RUN_WAIT, () -> {
            helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(4, 1, 2));
            helper.assertBlockPresent(Blocks.IRON_ORE, new BlockPos(4, 2, 2));
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(4, 3, 2));
            CoreGameTests.run(helper, source, "ma clear 0 remove minecraft:bedrock");
            helper.runAfterDelay(RUN_WAIT, () -> {
                helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(4, 1, 2));
                CoreGameTests.run(helper, source, "ma clear 0 remove minecraft:bedrock false");
                helper.runAfterDelay(RUN_WAIT, () -> {
                    helper.assertBlockNotPresent(Blocks.BEDROCK, new BlockPos(4, 1, 2));
                    helper.succeed();
                });
            });
        });
    }

    public static void clearKeepOresAndModdedLeavesOres(GameTestHelper helper) {
        requireIdle(helper);
        helper.setBlock(new BlockPos(4, 1, 2), Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(4, 2, 2), Blocks.DIRT);
        CommandSourceStack source = sourceAt(helper, new BlockPos(4, 4, 2));
        CoreGameTests.run(helper, source, "ma clear 0 keep ores_and_modded");
        helper.runAfterDelay(RUN_WAIT, () -> {
            helper.assertBlockPresent(Blocks.IRON_ORE, new BlockPos(4, 1, 2));
            helper.assertBlockNotPresent(Blocks.DIRT, new BlockPos(4, 2, 2));
            helper.succeed();
        });
    }

    public static void drainRemovesConnectedFluidWithinRadius(GameTestHelper helper) {
        requireIdle(helper);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(8, 1, 1), Blocks.WATER);
        BlockPos start = helper.absolutePos(new BlockPos(1, 1, 1));
        CommandSourceStack source = sourceAt(helper, new BlockPos(8, 2, 8));
        CoreGameTests.run(helper, source, "ma drain " + start.getX() + " " + start.getY() + " " + start.getZ() + " 2");
        helper.runAfterDelay(RUN_WAIT, () -> {
            helper.assertBlockNotPresent(Blocks.WATER, new BlockPos(1, 1, 1));
            helper.assertBlockNotPresent(Blocks.WATER, new BlockPos(2, 1, 1));
            helper.assertBlockNotPresent(Blocks.WATER, new BlockPos(3, 1, 1));
            helper.assertBlockPresent(Blocks.WATER, new BlockPos(8, 1, 1));
            helper.succeed();
        });
    }

    public static void killAllSkipsProtectedAndPlayers(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 4));
        Minecart cart = helper.spawn(EntityType.MINECART, new BlockPos(6, 1, 4));
        ServerPlayer player = CoreGameTests.fakePlayer(helper, new BlockPos(8, 1, 8));
        CoreGameTests.run(helper, CoreGameTests.source(player), "ma kill all");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(zombie.isRemoved(), "zombie should be removed");
            helper.assertFalse(cart.isRemoved(), "minecart is tag protected and should survive");
            CoreGameTests.run(helper, CoreGameTests.source(player), "ma kill by minecraft:minecart");
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(cart.isRemoved(), "kill by should bypass the protection tag");
                helper.succeed();
            });
        });
    }

    public static void tpdMovesVehicleWithPassenger(GameTestHelper helper) {
        Minecart cart = helper.spawn(EntityType.MINECART, new BlockPos(4, 1, 4));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 4));
        zombie.startRiding(cart, true, true);
        helper.assertTrue(zombie.isPassenger(), "zombie should be riding the minecart");
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "nether should exist");
        ServerPlayer player = CoreGameTests.fakePlayer(helper, new BlockPos(8, 1, 8));
        BlockPos origin = cart.blockPosition();
        nether.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
        CoreGameTests.run(helper, CoreGameTests.source(player), "ma tpd minecraft:the_nether @e[type=minecraft:minecart,distance=..10]");
        helper.assertTrue(cart.isRemoved(), "original minecart should have been removed from the overworld");
        AABB column = new AABB(origin.getX() - 4, nether.getMinY(), origin.getZ() - 4, origin.getX() + 4, nether.getMaxY() + 1, origin.getZ() + 4);
        helper.succeedWhen(() -> {
            helper.assertTrue(nether.isPositionEntityTicking(origin), "waiting for the nether chunk to become entity ticking");
            List<Minecart> carts = nether.getEntitiesOfClass(Minecart.class, column);
            helper.assertTrue(carts.size() == 1, "expected one minecart in the nether, found " + carts.size());
            List<Entity> passengers = carts.get(0).getPassengers();
            helper.assertTrue(passengers.size() == 1 && passengers.get(0) instanceof Zombie, "passenger should still be riding");
            carts.get(0).getPassengers().forEach(Entity::discard);
            carts.get(0).discard();
            nether.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, false);
        });
    }

    public static void scanOresWritesReport(GameTestHelper helper) {
        requireIdle(helper);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.GOLD_ORE);
        long before = countReports(ReportWriter.Family.ORES);
        CommandSourceStack source = sourceAt(helper, new BlockPos(8, 1, 8));
        CoreGameTests.run(helper, source, "ma scanOres 0 0 10");
        helper.runAfterDelay(RUN_WAIT, () -> {
            long after = countReports(ReportWriter.Family.ORES);
            helper.assertTrue(after >= before + 2, "expected two new ore reports, before " + before + " after " + after);
            helper.succeed();
        });
    }

    public static void locateBlockListsNearestFirstAndWritesReport(GameTestHelper helper) {
        requireIdle(helper);
        BlockPos near = helper.absolutePos(new BlockPos(6, 1, 6));
        BlockPos far = helper.absolutePos(new BlockPos(1, 5, 1));
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.BUDDING_AMETHYST);
        helper.setBlock(new BlockPos(1, 5, 1), Blocks.BUDDING_AMETHYST);
        long before = countReports(ReportWriter.Family.BLOCKS);
        CommandSourceStack source = sourceAt(helper, new BlockPos(8, 1, 8));

        BlockLocator locator = new BlockLocator(Blocks.BUDDING_AMETHYST, source.getPosition());
        ChunkPos chunk = ChunkPos.containing(near);
        locator.scanChunk(helper.getLevel().getChunk(chunk.x(), chunk.z()), chunk);
        ChunkPos farChunk = ChunkPos.containing(far);
        if (!farChunk.equals(chunk)) {
            locator.scanChunk(helper.getLevel().getChunk(farChunk.x(), farChunk.z()), farChunk);
        }
        helper.assertTrue(locator.total() == 2, "expected the two placed blocks, found " + locator.total());
        helper.assertTrue(locator.nearest().get(0).pos().equals(near), "nearest hit should be " + near + " but was " + locator.nearest().get(0).pos());

        CoreGameTests.run(helper, source, "ma locateBlock minecraft:budding_amethyst 0");
        helper.runAfterDelay(RUN_WAIT, () -> {
            long after = countReports(ReportWriter.Family.BLOCKS);
            helper.assertTrue(after >= before + 1, "expected a new block report, before " + before + " after " + after);
            helper.succeed();
        });
    }

    public static void radiusAboveLimitIsRefused(GameTestHelper helper) {
        requireIdle(helper);
        CommandSourceStack source = sourceAt(helper, new BlockPos(8, 1, 8));
        CoreGameTests.run(helper, source, "ma clear 999 keep nothing");
        CoreGameTests.run(helper, source, "ma scanOres 999");
        CoreGameTests.run(helper, source, "ma minearea 999");
        helper.runAfterDelay(2, () -> {
            helper.assertFalse(RunScheduler.isBusy(), "no run should have started for an out-of-range radius");
            helper.succeed();
        });
    }

    public static void aliasAndLowercaseLiteralsWork(GameTestHelper helper) {
        requireIdle(helper);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack source = sourceAt(helper, new BlockPos(8, 1, 8));
        helper.assertTrue(dispatcher.parse("modpackassistant cancel", source).getReader().canRead() == false, "full root should parse");
        helper.assertTrue(dispatcher.parse("ma cancel", source).getReader().canRead() == false, "alias should parse");
        helper.assertTrue(dispatcher.parse("ma scanores 0", source).getExceptions().isEmpty(), "lowercase literal should parse");
        helper.assertTrue(dispatcher.parse("ma scanOres 0", source).getExceptions().isEmpty(), "camel case literal should parse");
        helper.succeed();
    }

    public static void structureLootPlacesAndClears(GameTestHelper helper) {
        TestLootPlacements record = TestLootPlacements.get(helper.getLevel().getServer());
        record.clear();
        ServerPlayer player = CoreGameTests.fakePlayer(helper, new BlockPos(2, 1, 12));
        CommandSourceStack source = CoreGameTests.source(player);
        CoreGameTests.run(helper, source, "ma testStructureLoot minecraft:village_plains 1");
        helper.runAfterDelay(5, () -> {
            helper.assertFalse(record.isEmpty(), "placements should be recorded");
            int placed = record.positions().size();
            helper.assertTrue(placed > 0 && placed % 2 == 0, "expected chest and sign pairs, got " + placed);
            CoreGameTests.run(helper, source, "ma testStructureLoot clear");
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(record.isEmpty(), "record should be cleared");
                helper.succeed();
            });
        });
    }
}
