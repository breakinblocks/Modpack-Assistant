package com.breakinblocks.modpackassistant.commands.world;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.commands.args.ClearKeepArgument;
import com.breakinblocks.modpackassistant.commands.args.ClearKeepArgument.ClearKeep;
import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.jobs.ChunkAccessor;
import com.breakinblocks.modpackassistant.jobs.RegionCommands;
import com.breakinblocks.modpackassistant.jobs.RegionGeometry;
import com.breakinblocks.modpackassistant.jobs.Run;
import com.breakinblocks.modpackassistant.jobs.RunScheduler;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public final class ClearCommand {
    private ClearCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext buildContext) {
        return Commands.literal("clear")
                .requires(MAPermissions.GAMEMASTER)
                .then(Commands.argument("radius", IntegerArgumentType.integer(0))
                        .executes(context -> clear(context, keepRule(ClearKeep.ORES), "keep ores", true))
                        .then(Commands.literal("keep")
                                .then(withBedrockFlag(Commands.argument("keep", ClearKeepArgument.clearKeep()),
                                        (context, protect) -> clear(context, keepRule(ClearKeepArgument.get(context, "keep")), "keep " + ClearKeepArgument.get(context, "keep").getSerializedName(), protect))))
                        .then(Commands.literal("remove")
                                .then(withBedrockFlag(Commands.argument("predicate", BlockPredicateArgument.blockPredicate(buildContext)),
                                        (context, protect) -> clear(context, BlockPredicateArgument.getBlockPredicate(context, "predicate"), "remove matching blocks", protect)))));
    }

    private interface Executor {
        int run(CommandContext<CommandSourceStack> context, boolean protectBedrock) throws CommandSyntaxException;
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withBedrockFlag(T node, Executor executor) {
        return node.executes(context -> executor.run(context, true))
                .then(Commands.argument("protect_bedrock", BoolArgumentType.bool())
                        .executes(context -> executor.run(context, BoolArgumentType.getBool(context, "protect_bedrock"))));
    }

    private static Predicate<BlockInWorld> keepRule(ClearKeep keep) {
        return switch (keep) {
            case ORES -> block -> !block.getState().is(Tags.Blocks.ORES);
            case ORES_AND_MODDED -> block -> {
                BlockState state = block.getState();
                if (state.is(Tags.Blocks.ORES)) {
                    return false;
                }
                ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                return ResourceLocation.DEFAULT_NAMESPACE.equals(key.getNamespace());
            };
            case NOTHING -> block -> true;
        };
    }

    private static int clear(CommandContext<CommandSourceStack> context, Predicate<BlockInWorld> removeRule, String description, boolean protectBedrock) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandResults.player(source);
        ServerLevel level = source.getLevel();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        RegionGeometry region = RegionCommands.region(source, player, radius, MAConfig.maxClearRadius(), "max_clear_radius");
        if (region == null) {
            return 0;
        }

        AtomicLong removed = new AtomicLong();
        int minY = RegionGeometry.minY(level);
        int maxY = RegionGeometry.maxY(level);
        Run run = new Run(source, "region clear (" + description + ")", level.dimension());
        for (ChunkPos chunk : region.chunks()) {
            run.job(() -> removed.addAndGet(ChunkAccessor.withChunk(level, chunk, loaded -> clearChunk(level, chunk, minY, maxY, removeRule, protectBedrock))));
        }
        run.onComplete(finished -> finished.message(Messages.CLEAR_DONE.get(removed.get(), region.chunkCount())));

        if (!RunScheduler.tryStart(run)) {
            return 0;
        }
        run.message(Messages.CLEAR_WARNING.get(region.spanText(), region.chunkCount()).withStyle(ChatFormatting.RED));
        RegionCommands.reportUnloaded(run, level, region);
        return run.total();
    }

    private static long clearChunk(ServerLevel level, ChunkPos chunk, int minY, int maxY, Predicate<BlockInWorld> removeRule, boolean protectBedrock) {
        BlockState air = Blocks.AIR.defaultBlockState();
        List<BlockPos> changed = new ArrayList<>();
        RegionGeometry.forEachBlock(chunk, minY, maxY, true, pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return;
            }
            if (protectBedrock && state.is(Blocks.BEDROCK)) {
                return;
            }
            if (!removeRule.test(new BlockInWorld(level, pos, true))) {
                return;
            }
            BlockPos immutable = pos.immutable();
            level.setBlock(immutable, air, Block.UPDATE_CLIENTS);
            changed.add(immutable);
        });
        for (BlockPos pos : changed) {
            level.blockUpdated(pos, Blocks.AIR);
        }
        return changed.size();
    }
}
