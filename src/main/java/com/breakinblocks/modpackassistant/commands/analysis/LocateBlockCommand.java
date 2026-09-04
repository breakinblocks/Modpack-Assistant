package com.breakinblocks.modpackassistant.commands.analysis;

import com.breakinblocks.modpackassistant.analysis.BlockLocator;
import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.jobs.ChunkAccessor;
import com.breakinblocks.modpackassistant.jobs.RegionCommands;
import com.breakinblocks.modpackassistant.jobs.RegionGeometry;
import com.breakinblocks.modpackassistant.jobs.Run;
import com.breakinblocks.modpackassistant.jobs.RunScheduler;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class LocateBlockCommand {
    private static final int REPORTED = 10;

    private LocateBlockCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return Commands.literal("locateBlock")
                .requires(MAPermissions.GAMEMASTER)
                .then(Commands.argument("block", BlockStateArgument.block(context))
                        .then(Commands.argument("chunk_radius", IntegerArgumentType.integer(0))
                                .executes(LocateBlockCommand::locate)));
    }

    private static int locate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandResults.player(source);
        ServerLevel level = source.getLevel();
        Block block = BlockStateArgument.getBlock(context, "block").getState().getBlock();
        int radius = IntegerArgumentType.getInteger(context, "chunk_radius");
        RegionGeometry region = RegionCommands.region(source, player, radius, MAConfig.maxScanRadius(), "max_scan_radius");
        if (region == null) {
            return 0;
        }

        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        BlockLocator locator = new BlockLocator(block, source.getPosition());
        ReportWriter.Context reportContext = new ReportWriter.Context(source, "/ma locateBlock " + blockId + " " + radius)
                .note("block", blockId)
                .note("chunk_span", region.span() + "x" + region.span())
                .note("origin", BlockPos.containing(source.getPosition()).toShortString());

        Run run = new Run(source, "block search", level.dimension());
        for (ChunkPos chunk : region.chunks()) {
            run.job(() -> ChunkAccessor.withChunk(level, chunk, loaded -> {
                locator.scanChunk(loaded, chunk);
                return null;
            }));
        }
        run.onComplete(finished -> {
            printChat(finished, locator, region);
            if (locator.total() > 0) {
                ReportWriter.deliver(finished, ReportWriter.Family.BLOCKS, "locate-" + ReportWriter.sanitize(blockId), "csv", locator.csv(reportContext));
            }
        });

        if (!RunScheduler.tryStart(run)) {
            return 0;
        }
        run.message(Messages.LOCATE_START.get(block.getName(), region.spanText(), region.chunkCount()));
        RegionCommands.reportUnloaded(run, level, region);
        return run.total();
    }

    private static void printChat(Run run, BlockLocator locator, RegionGeometry region) {
        if (locator.total() == 0) {
            run.message(Messages.LOCATE_NONE.get(locator.block().getName(), region.spanText()).withStyle(ChatFormatting.RED));
            return;
        }
        run.message(Messages.LOCATE_HEADER.get(locator.total(), locator.block().getName(), region.spanText()).withStyle(ChatFormatting.GREEN));
        List<BlockLocator.Hit> nearest = locator.nearest();
        for (BlockLocator.Hit hit : nearest.subList(0, Math.min(REPORTED, nearest.size()))) {
            run.message(line(hit));
        }
    }

    private static MutableComponent line(BlockLocator.Hit hit) {
        BlockPos pos = hit.pos();
        String teleport = "/tp @s " + pos.getX() + " " + (pos.getY() + 1) + " " + pos.getZ();
        MutableComponent coords = Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ())
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, teleport))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Messages.LOCATE_TELEPORT.get())));
        return Component.literal("  ").append(coords)
                .append(Messages.LOCATE_DISTANCE.get(Math.round(hit.distance())).withStyle(ChatFormatting.GRAY));
    }
}
