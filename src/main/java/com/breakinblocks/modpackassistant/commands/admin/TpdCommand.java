package com.breakinblocks.modpackassistant.commands.admin;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class TpdCommand {
    private TpdCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tpd")
                .requires(MAPermissions.GAMEMASTER)
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(context -> teleport(context.getSource(), DimensionArgument.getDimension(context, "dimension"), List.of(CommandResults.player(context.getSource()))))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> teleport(context.getSource(), DimensionArgument.getDimension(context, "dimension"), EntityArgument.getEntities(context, "targets")))));
    }

    private static int teleport(CommandSourceStack source, ServerLevel destination, Collection<? extends Entity> targets) throws CommandSyntaxException {
        int moved = 0;
        for (Entity entity : new ArrayList<>(targets)) {
            if (entity.isRemoved()) {
                continue;
            }
            BlockPos pos = arrivalPosition(destination, entity);
            destination.getChunk(pos.getX() >> 4, pos.getZ() >> 4);

            int cleared = clearPocket(destination, pos, entity);
            if (cleared > 0) {
                source.sendSuccess(() -> Messages.TPD_CLEARED.get(cleared, entity.getDisplayName()), false);
            }

            int level = 0;
            float progress = 0.0F;
            if (entity instanceof ServerPlayer player) {
                level = player.experienceLevel;
                progress = player.experienceProgress;
            }

            Entity result = entity.changeDimension(new DimensionTransition(destination, Vec3.atBottomCenterOf(pos), Vec3.ZERO, entity.getYRot(), entity.getXRot(), DimensionTransition.DO_NOTHING));
            if (result == null) {
                source.sendFailure(Messages.TPD_ENTITY_FAILED.get(entity.getDisplayName(), destination.dimension().location()));
                continue;
            }
            if (result instanceof ServerPlayer player) {
                player.setExperienceLevels(level);
                player.setExperiencePoints((int) (progress * player.getXpNeededForNextLevel()));
            }
            moved++;
        }
        return CommandResults.success(source, Messages.TPD_DONE.get(moved, destination.dimension().location()), moved);
    }

    private static BlockPos arrivalPosition(ServerLevel destination, Entity entity) {
        BlockPos pos = entity.blockPosition();
        WorldBorder border = destination.getWorldBorder();
        if (!border.isWithinBounds(pos)) {
            pos = new BlockPos((int) border.getCenterX(), 0, (int) border.getCenterZ());
            return destination.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        }
        if (pos.getY() < destination.getMinBuildHeight() || pos.getY() >= destination.getMaxBuildHeight()) {
            return destination.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        }
        return pos;
    }

    private static int clearPocket(ServerLevel level, BlockPos pos, Entity entity) {
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(pos));
        if (level.noCollision(box)) {
            return 0;
        }
        int cleared = 0;
        for (BlockPos target : BlockPos.betweenClosed(
                (int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
                (int) Math.ceil(box.maxX) - 1, (int) Math.ceil(box.maxY) - 1, (int) Math.ceil(box.maxZ) - 1)) {
            if (!level.getBlockState(target).isAir()) {
                level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
                cleared++;
            }
        }
        return cleared;
    }
}
