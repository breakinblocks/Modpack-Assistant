package com.breakinblocks.modpackassistant.commands.world;

import com.breakinblocks.modpackassistant.commands.items.ItemStrings;
import java.util.concurrent.atomic.AtomicInteger;
import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.analysis.DropAccumulator;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.commands.args.HarvestModeArgument;
import com.breakinblocks.modpackassistant.commands.args.HarvestModeArgument.HarvestMode;
import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.jobs.ChunkAccessor;
import com.breakinblocks.modpackassistant.jobs.RegionCommands;
import com.breakinblocks.modpackassistant.jobs.RegionGeometry;
import com.breakinblocks.modpackassistant.jobs.Run;
import com.breakinblocks.modpackassistant.jobs.RunScheduler;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import java.text.DecimalFormat;
import java.util.List;

public final class MineAreaCommand {
    private static final DecimalFormat GROUPED = new DecimalFormat("#,###");
    private static final DecimalFormat PERCENT = new DecimalFormat("0.00");
    private static final int GRID_WIDTH = 6;
    private static final int GRID_ROWS = 10;

    private MineAreaCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("minearea")
                .requires(MAPermissions.GAMEMASTER)
                .then(Commands.argument("radius", IntegerArgumentType.integer(0))
                        .executes(context -> mine(context, HarvestMode.FORTUNE_3))
                        .then(Commands.argument("harvest", HarvestModeArgument.harvestMode()).suggests(HarvestModeArgument::suggest)
                                .executes(context -> mine(context, HarvestModeArgument.get(context, "harvest")))));
    }

    private static int mine(CommandContext<CommandSourceStack> context, HarvestMode mode) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandResults.player(source);
        ServerLevel level = source.getLevel();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        RegionGeometry region = RegionCommands.region(source, player, radius, MAConfig.maxScanRadius(), "max_scan_radius");
        if (region == null) {
            return 0;
        }

        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        if (mode.enchantment() != null) {
            var holder = source.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(mode.enchantment());
            EnchantmentHelper.updateEnchantments(tool, mutable -> mutable.set(holder, mode.level()));
        }

        DropAccumulator drops = new DropAccumulator();
        AtomicInteger scanned = new AtomicInteger();
        Direction facing = player.getDirection();
        BlockPos barrelOrigin = player.blockPosition().relative(facing);
        int minY = RegionGeometry.minY(level);
        int maxY = RegionGeometry.maxY(level);
        Run run = new Run(source, "mining simulation", level.dimension());
        for (ChunkPos chunk : region.chunks()) {
            run.job(() -> ChunkAccessor.withLoadedChunk(level, chunk, loaded -> {
                scanned.incrementAndGet();
                RegionGeometry.forEachBlock(chunk, minY, maxY, false, pos -> {
                    BlockState state = loaded.getBlockState(pos);
                    if (state.isAir() || state.is(Blocks.BEDROCK) || !state.is(Tags.Blocks.ORES)) {
                        return;
                    }
                    for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool)) {
                        drops.add(drop);
                    }
                });
            }));
        }
        run.onComplete(finished -> {
            printSummary(finished, drops, mode);
            placeBarrels(finished, level, barrelOrigin, facing, drops);
            if (scanned.get() < region.chunkCount()) finished.message(Messages.SCAN_SKIPPED.get(region.chunkCount() - scanned.get()));
        });

        if (!RunScheduler.tryStart(run)) {
            return 0;
        }
        run.message(Messages.MINE_START.get(mode.getSerializedName(), region.spanText(), region.chunkCount()));
        run.message(Messages.SCAN_LOADED_ONLY.get());
        return run.total();
    }

    private static void printSummary(Run run, DropAccumulator drops, HarvestMode mode) {
        long total = drops.total();
        if (total == 0) {
            run.message(Messages.MINE_NONE.get().withStyle(ChatFormatting.RED));
            return;
        }
        run.message(Messages.MINE_HEADER.get(mode.getSerializedName(), GROUPED.format(total)).withStyle(ChatFormatting.GREEN));
        List<DropAccumulator.Drop> ranked = drops.ranked();
        if (ranked.size() > 10) run.message(Messages.MINE_SUMMARY_LIMIT.get(ranked.size()));
        for (DropAccumulator.Drop entry : ranked.subList(0, Math.min(10, ranked.size()))) {
            double percent = entry.count() * 100.0D / total;
            run.message(Component.empty()
                    .append(Component.literal("[" + GROUPED.format(entry.count()) + "]").withStyle(Style.EMPTY
                            .withColor(ChatFormatting.YELLOW)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Messages.SCAN_PERCENT.get(PERCENT.format(percent))))))
                    .append(Component.literal(" " + ItemStrings.giveString(entry.stack(), run.source().registryAccess())).withStyle(ChatFormatting.WHITE)));
        }
    }

    private static void placeBarrels(Run run, ServerLevel level, BlockPos origin, Direction facing, DropAccumulator drops) {
        List<DropAccumulator.Drop> remaining = drops.ranked();
        if (remaining.isEmpty()) {
            return;
        }
        Direction right = facing.getClockWise();
        int placed = 0;
        int index = 0;
        long[] left = new long[remaining.size()];
        for (int i = 0; i < left.length; i++) {
            left[i] = remaining.get(i).count();
        }

        for (int row = 0; row < GRID_ROWS && index < remaining.size(); row++) {
            for (int column = 0; column < GRID_WIDTH && index < remaining.size(); column++) {
                BlockPos pos = origin.relative(facing, row).relative(right, column - GRID_WIDTH / 2);
                if (!level.getBlockState(pos).canBeReplaced()) {
                    continue;
                }
                level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
                if (!(level.getBlockEntity(pos) instanceof Container container)) {
                    continue;
                }
                placed++;
                for (int slot = 0; slot < container.getContainerSize() && index < remaining.size(); slot++) {
                    ItemStack stack = remaining.get(index).stack();
                    int take = (int) Math.min(left[index], stack.getMaxStackSize());
                    container.setItem(slot, stack.copyWithCount(take));
                    left[index] -= take;
                    if (left[index] <= 0) {
                        index++;
                    }
                }
                container.setChanged();
            }
        }
        run.message(Messages.MINE_BARRELS.get(placed, origin.toShortString()));
        if (index < remaining.size()) {
            run.message(Messages.MINE_UNPLACED.get(remaining.size() - index));
        }
    }
}
