package com.breakinblocks.modpackassistant.commands.world;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.jobs.Run;
import com.breakinblocks.modpackassistant.jobs.RunScheduler;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

public final class DrainCommand {
    public static final int MAX_RADIUS = 300;
    private static final int SCAN_PER_JOB = 20_000;
    private static final int REMOVE_PER_JOB = 4_096;

    private DrainCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("drain")
                .requires(MAPermissions.GAMEMASTER)
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                        .executes(context -> drain(context.getSource(), CommandResults.player(context.getSource()).blockPosition(), IntegerArgumentType.getInteger(context, "radius"))))
                .then(Commands.argument("location", BlockPosArgument.blockPos())
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                                .executes(context -> drain(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "location"), IntegerArgumentType.getInteger(context, "radius")))));
    }

    private static int drain(CommandSourceStack source, BlockPos requested, int radius) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        BlockPos start = findFluid(level, requested);
        if (start == null) {
            return CommandResults.fail(source, Messages.DRAIN_NO_FLUID.get(requested.toShortString()));
        }
        Fluid fluid = level.getFluidState(start).getType();
        DrainRun state = new DrainRun(level, start, fluid, radius, MAConfig.maxDrainBlocks());

        Run run = new Run(source, "fluid drain", level.dimension());
        run.job(() -> state.scan(run));
        run.onComplete(finished -> {
            String fluidName = BuiltInRegistries.FLUID.getKey(fluid).toString();
            if (state.truncated) {
                finished.message(Messages.DRAIN_TRUNCATED.get(state.cap, state.removed));
            }
            finished.message(Messages.DRAIN_DONE.get(state.removed, fluidName));
        });

        if (!RunScheduler.tryStart(run)) {
            return 0;
        }
        run.message(Messages.DRAIN_START.get(BuiltInRegistries.FLUID.getKey(fluid), start.toShortString(), radius));
        return run.total();
    }

    @Nullable
    private static BlockPos findFluid(ServerLevel level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty()) {
            return pos;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (!level.getFluidState(neighbour).isEmpty()) {
                return neighbour;
            }
        }
        return null;
    }

    private static final class DrainRun {
        private final ServerLevel level;
        private final Fluid fluid;
        private final BoundingBox bounds;
        private final int cap;
        private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        private final LongOpenHashSet visited = new LongOpenHashSet();
        private final LongArrayList found = new LongArrayList();
        private boolean truncated;
        private int removed;

        DrainRun(ServerLevel level, BlockPos start, Fluid fluid, int radius, int cap) {
            this.level = level;
            this.fluid = fluid;
            this.bounds = new BoundingBox(start).inflatedBy(radius);
            this.cap = cap;
            long key = start.asLong();
            queue.enqueue(key);
            visited.add(key);
            found.add(key);
        }

        void scan(Run run) {
            int budget = SCAN_PER_JOB;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            while (!queue.isEmpty() && budget-- > 0) {
                long current = queue.dequeueLong();
                cursor.set(current);
                for (Direction direction : Direction.values()) {
                    BlockPos neighbour = cursor.relative(direction);
                    long key = neighbour.asLong();
                    if (visited.contains(key) || !bounds.isInside(neighbour)) {
                        continue;
                    }
                    FluidState state = level.getFluidState(neighbour);
                    if (state.isEmpty() || !state.getType().isSame(fluid)) {
                        continue;
                    }
                    visited.add(key);
                    if (found.size() >= cap) {
                        truncated = true;
                        queue.clear();
                        break;
                    }
                    found.add(key);
                    queue.enqueue(key);
                }
            }
            if (!queue.isEmpty()) {
                run.job(() -> scan(run));
                return;
            }
            for (int offset = 0; offset < found.size(); offset += REMOVE_PER_JOB) {
                int from = offset;
                int to = Math.min(found.size(), offset + REMOVE_PER_JOB);
                run.job(() -> remove(from, to));
            }
        }

        void remove(int from, int to) {
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int i = from; i < to; i++) {
                cursor.set(found.getLong(i));
                BlockState state = level.getBlockState(cursor);
                if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
                    level.setBlock(cursor, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_CLIENTS);
                } else if (!state.getFluidState().isEmpty()) {
                    level.setBlock(cursor, air, Block.UPDATE_CLIENTS);
                } else {
                    continue;
                }
                removed++;
            }
            for (int i = from; i < to; i++) {
                cursor.set(found.getLong(i));
                level.blockUpdated(cursor, level.getBlockState(cursor).getBlock());
            }
        }
    }
}
