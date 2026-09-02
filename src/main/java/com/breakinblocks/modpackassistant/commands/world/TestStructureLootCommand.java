package com.breakinblocks.modpackassistant.commands.world;

import com.breakinblocks.modpackassistant.analysis.LootContexts;
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
import net.minecraft.commands.arguments.ResourceArgument;
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
                .then(Commands.argument("structure", ResourceArgument.resource(buildContext, Registries.STRUCTURE))
                        .executes(context -> place(context, 1))
                        .then(Commands.argument("samples", IntegerArgumentType.integer(1, MAX_SAMPLES))
                                .executes(context -> place(context, IntegerArgumentType.getInteger(context, "samples")))));
    }

    private static int place(CommandContext<CommandSourceStack> context, int samples) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandResults.player(source);
        ServerLevel level = source.getLevel();
        Holder.Reference<Structure> structure = ResourceArgument.getStructure(context, "structure");

        TestLootPlacements record = TestLootPlacements.get(level.getServer());
        if (!record.isEmpty()) {
            return CommandResults.fail(source, Messages.STRUCTLOOT_STANDING.get(record.positions().size()));
        }

        StructureLootResolver.Result resolved = StructureLootResolver.resolve(level, structure);
        if (resolved.isEmpty()) {
            return CommandResults.fail(source, Messages.STRUCTLOOT_NONE.get(structure.key().identifier(), String.join(", ", resolved.tried())));
        }

        Direction facing = player.getDirection();
        Direction right = facing.getClockWise();
        BlockPos origin = player.blockPosition().relative(facing, 3);
        List<BlockPos> placed = new ArrayList<>();
        int chests = 0;
        int confirmed = 0;
        int heuristic = 0;
        int row = 0;
        int column = 0;

        for (StructureLootResolver.Found found : resolved.tables()) {
            if (found.confirmed()) {
                confirmed++;
            } else {
                heuristic++;
            }
            LootTable table = level.getServer().reloadableRegistries().getLootTable(found.table());
            for (int sample = 1; sample <= samples; sample++) {
                LootContexts.Built built = LootContexts.build(level, origin, player, player.getLuck(), table.getParamSet());
                List<ItemStack> loot = built.ok() ? table.getRandomItems(built.params()) : List.of();
                int parts = Math.max(1, (loot.size() + CHEST_SLOTS - 1) / CHEST_SLOTS);
                for (int part = 0; part < parts; part++) {
                    BlockPos chestPos = origin.relative(facing, row * 2).relative(right, column);
                    BlockPos signPos = chestPos.relative(facing.getOpposite());
                    column++;
                    if (column >= 8) {
                        column = 0;
                        row++;
                    }
                    if (!level.getBlockState(chestPos).canBeReplaced() || !level.getBlockState(signPos).canBeReplaced()) {
                        continue;
                    }
                    level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
                    if (level.getBlockEntity(chestPos) instanceof Container container) {
                        int from = part * CHEST_SLOTS;
                        int to = Math.min(loot.size(), from + CHEST_SLOTS);
                        for (int i = from; i < to; i++) {
                            container.setItem(i - from, loot.get(i));
                        }
                        container.setChanged();
                    }
                    placeSign(level, signPos, facing.getOpposite(), found, samples > 1 ? sample : 0, parts > 1 ? part + 1 : 0, built);
                    placed.add(chestPos);
                    placed.add(signPos);
                    chests++;
                }
            }
        }

        record.record(level.dimension(), placed);
        return CommandResults.success(source, Messages.STRUCTLOOT_PLACED.get(chests, resolved.tables().size(), origin.toShortString(), confirmed, heuristic), chests);
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
        int removed = 0;
        if (level != null) {
            for (BlockPos pos : record.positions()) {
                BlockState state = level.getBlockState(pos);
                if (!state.is(Blocks.CHEST) && !state.is(Blocks.OAK_WALL_SIGN)) {
                    continue;
                }
                if (level.getBlockEntity(pos) instanceof Container container) {
                    container.clearContent();
                }
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                removed++;
            }
        }
        record.clear();
        return CommandResults.success(source, Messages.STRUCTLOOT_CLEARED.get(removed), removed);
    }
}
