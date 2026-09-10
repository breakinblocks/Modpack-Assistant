package com.breakinblocks.modpackassistant.commands.world;

import com.breakinblocks.modpackassistant.analysis.LootContexts;
import com.breakinblocks.modpackassistant.analysis.LootSafety;
import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.jobs.Run;
import com.breakinblocks.modpackassistant.jobs.RunScheduler;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.util.RandomSource;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import com.breakinblocks.modpackassistant.analysis.StructureLootResolver;
import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.data.TestLootPlacements;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;

public final class TestStructureLootCommand {
    public static final int MAX_SAMPLES = 16;
    private static final int CHEST_SLOTS = 27;
    private static final int SIGN_LINE = 15;

    private TestStructureLootCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext buildContext) {
        return Commands.literal("testStructureLoot")
                .requires(MAPermissions.GAMEMASTER)
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource())))
                .then(Commands.argument("structure", ResourceKeyArgument.key(Registries.STRUCTURE))
                        .executes(context -> place(context, 1))
                        .then(Commands.argument("samples", IntegerArgumentType.integer(1, MAX_SAMPLES))
                                .executes(context -> place(context, IntegerArgumentType.getInteger(context, "samples")))));
    }

    private static int place(CommandContext<CommandSourceStack> context, int samples) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandResults.player(source);
        ServerLevel level = source.getLevel();
        Holder.Reference<Structure> structure = ResourceKeyArgument.getStructure(context, "structure");

        TestLootPlacements record = TestLootPlacements.get(level.getServer());
        if (!record.isEmpty()) {
            return CommandResults.fail(source, Messages.STRUCTLOOT_STANDING.get(record.positions().size()));
        }

        Run run = new Run(source, "structure loot test", level.dimension());
        StructureLootResolver resolver = new StructureLootResolver(level, structure);
        Placement work = new Placement(level, player, record, samples, run);
        run.repeat(() -> {
            if (!resolver.step()) return false;
            StructureLootResolver.Result result = resolver.result();
            if (result.isEmpty()) {
                run.message(Messages.STRUCTLOOT_NONE.get(structure.key().identifier(), String.join(", ", result.tried())));
            } else {
                work.tables = result.tables();
                run.repeat(work::step);
            }
            return true;
        });
        run.onComplete(finished -> finished.message(Messages.STRUCTLOOT_PLACED.get(work.chests, work.tables.size(),
                work.origin.toShortString(), work.tables.stream().filter(StructureLootResolver.Found::confirmed).count(),
                work.tables.stream().filter(found -> !found.confirmed()).count())));
        if (!RunScheduler.tryStart(run)) return 0;
        run.message(Messages.STRUCTLOOT_START.get(structure.key().identifier(), work.limit));
        run.message(Messages.STRUCTLOOT_RULES.get());
        return 1;
    }

    private static final class Placement {
        final ServerLevel level;
        final ServerPlayer player;
        final TestLootPlacements record;
        final int samples;
        final Run run;
        final int limit = MAConfig.maxStructureLootChests();
        final Direction facing;
        final BlockPos origin;
        final float luck;
        final RandomSource random = RandomSource.create();
        List<StructureLootResolver.Found> tables = List.of();
        int tableIndex;
        int sample = 1;
        int part;
        int gridIndex;
        int chests;
        LootSafety safety;
        LootTable table;
        LootContexts.Built built;
        List<ItemStack> loot;

        Placement(ServerLevel level, ServerPlayer player, TestLootPlacements record, int samples, Run run) {
            this.level = level;
            this.player = player;
            this.record = record;
            this.samples = samples;
            this.run = run;
            facing = player.getDirection();
            origin = player.blockPosition().relative(facing, 3);
            luck = player.getLuck();
        }

        boolean step() {
            if (tableIndex >= tables.size()) return true;
            if (gridIndex >= limit) {
                run.message(Messages.STRUCTLOOT_LIMIT.get(limit));
                return true;
            }
            StructureLootResolver.Found found = tables.get(tableIndex);
            if (safety == null) {
                safety = new LootSafety(level, found.table(), luck);
                table = level.getServer().reloadableRegistries().getLootTable(found.table());
            }
            safety.verifyCurrent();
            if (!safety.complete()) {
                try {
                    safety.step();
                } catch (IllegalArgumentException error) {
                    run.message(Messages.STRUCTLOOT_SKIPPED.get(found.table().identifier(), error.getMessage()));
                    nextTable();
                }
                return false;
            }
            if (loot == null) {
                built = LootContexts.build(level, origin, player, luck, table.getParamSet());
                if (!built.ok()) {
                    run.message(Messages.LOOT_MISSING_PARAMS.get(found.table().identifier(), String.join(", ", built.missing()), table.getParamSet()));
                    nextTable();
                    return false;
                }
                loot = new ArrayList<>();
                int stackLimit = (limit - gridIndex) * CHEST_SLOTS;
                table.getRandomItemsRaw(new LootContext.Builder(built.params()).withOptionalRandomSource(random).create(Optional.empty()), stack -> {
                    if (stack.isEmpty()) return;
                    int left = stack.getCount();
                    while (left > 0) {
                        if (loot.size() >= stackLimit) throw new IllegalArgumentException(Messages.LOOT_OUTPUT_LIMIT.get().getString());
                        int count = Math.min(left, stack.getMaxStackSize());
                        loot.add(stack.copyWithCount(count));
                        left -= count;
                    }
                });
                return false;
            }
            int parts = Math.max(1, (loot.size() + CHEST_SLOTS - 1) / CHEST_SLOTS);
            BlockPos chestPos = origin.relative(facing, gridIndex / 8 * 2).relative(facing.getClockWise(), gridIndex % 8);
            BlockPos signPos = chestPos.relative(facing.getOpposite());
            gridIndex++;
            if (level.isInsideBuildHeight(chestPos) && level.getWorldBorder().isWithinBounds(chestPos)
                    && level.getWorldBorder().isWithinBounds(signPos)
                    && level.getBlockState(chestPos).canBeReplaced() && level.getBlockState(signPos).canBeReplaced()
                    && level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState())) {
                record.append(level.dimension(), chestPos);
                if (level.getBlockEntity(chestPos) instanceof Container container) {
                    int from = part * CHEST_SLOTS;
                    for (int i = from; i < Math.min(loot.size(), from + CHEST_SLOTS); i++) container.setItem(i - from, loot.get(i));
                    container.setChanged();
                }
                record.append(level.dimension(), signPos);
                placeSign(level, signPos, facing.getOpposite(), found, samples > 1 ? sample : 0, parts > 1 ? part + 1 : 0, built);
                chests++;
            }
            if (++part >= parts) {
                loot = null;
                part = 0;
                if (++sample > samples) nextTable();
            }
            return tableIndex >= tables.size();
        }

        private void nextTable() {
            tableIndex++;
            sample = 1;
            part = 0;
            loot = null;
            safety = null;
        }
    }

    private static void placeSign(ServerLevel level, BlockPos pos, Direction facing, StructureLootResolver.Found found, int sample, int part, LootContexts.Built built) {
        BlockState sign = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
        level.setBlockAndUpdate(pos, sign);
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity entity)) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(found.table().identifier().getNamespace());
        String path = found.table().identifier().getPath();
        while (!path.isEmpty() && lines.size() < 3) {
            int cut = Math.min(SIGN_LINE, path.length());
            lines.add(path.substring(0, cut));
            path = path.substring(cut);
        }
        StringBuilder last = new StringBuilder();
        if (!found.confirmed()) {
            last.append("heuristic ");
        }
        if (!built.ok()) {
            last.append("no context ");
        }
        if (sample > 0) {
            last.append("#").append(sample).append(' ');
        }
        if (part > 0) {
            last.append("part ").append(part);
        }
        lines.add(last.toString().trim());

        SignText text = new SignText();
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            text = text.setMessage(i, Component.literal(lines.get(i)));
        }
        entity.setText(text, true);
        entity.setWaxed(true);
    }

    private static int clear(CommandSourceStack source) {
        TestLootPlacements record = TestLootPlacements.get(source.getServer());
        if (record.isEmpty() || record.dimension() == null) {
            return CommandResults.fail(source, Messages.STRUCTLOOT_NOTHING.get());
        }
        ServerLevel level = source.getServer().getLevel(record.dimension());
        if (level == null) return CommandResults.fail(source, Messages.DIMENSION_NOT_FOUND.get());
        AtomicInteger removed = new AtomicInteger();
        Run run = new Run(source, "structure loot cleanup", level.dimension());
        run.repeat(() -> {
            if (record.isEmpty()) return true;
            BlockPos pos = record.positions().getLast();
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.CHEST) || state.is(Blocks.OAK_WALL_SIGN)) {
                if (level.getBlockEntity(pos) instanceof Container container) container.clearContent();
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                removed.incrementAndGet();
            }
            record.forget(pos);
            return record.isEmpty();
        });
        run.onComplete(finished -> finished.message(Messages.STRUCTLOOT_CLEARED.get(removed.get())));
        return RunScheduler.tryStart(run) ? 1 : 0;
    }
}
