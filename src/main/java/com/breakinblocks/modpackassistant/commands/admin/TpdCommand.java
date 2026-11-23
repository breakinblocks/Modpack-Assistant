package com.breakinblocks.modpackassistant.commands.admin;

import org.jetbrains.annotations.Nullable;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

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
            if (pos == null) {
                source.sendFailure(Messages.TPD_NO_SAFE_POSITION.get(entity.getDisplayName(), destination.dimension().location()));
                continue;
            }
            destination.getChunk(pos.getX() >> 4, pos.getZ() >> 4);

            int level = 0;
            float progress = 0.0F;
            if (entity instanceof ServerPlayer player) {
                level = player.experienceLevel;
                progress = player.experienceProgress;
            }

            Entity result = moveWithPassengers(entity, destination, Vec3.atBottomCenterOf(pos));
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
        return moved == 0 ? CommandResults.fail(source, Messages.TPD_NONE.get())
                : CommandResults.success(source, Messages.TPD_DONE.get(moved, destination.dimension().location()), moved);
    }

    @Nullable
    private static Entity moveWithPassengers(Entity entity, ServerLevel destination, Vec3 target) {
        List<Entity> passengers = List.copyOf(entity.getPassengers());
        entity.ejectPassengers();
        Entity moved = move(entity, destination, target);
        if (moved == null) {
            return null;
        }
        for (Entity passenger : passengers) {
            Entity movedPassenger = moveWithPassengers(passenger, destination, target);
            if (movedPassenger != null) {
                movedPassenger.startRiding(moved, true);
            }
        }
        return moved;
    }

    @Nullable
    private static Entity move(Entity entity, ServerLevel destination, Vec3 target) {
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(destination, target.x, target.y, target.z, player.getYRot(), player.getXRot());
            return player;
        }
        if (entity.level() == destination) {
            entity.moveTo(target.x, target.y, target.z, entity.getYRot(), entity.getXRot());
            return entity;
        }
        return entity.changeDimension(destination, new ITeleporter() {
            @Override
            public Entity placeEntity(Entity original, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                return repositionEntity.apply(false);
            }

            @Override
            public PortalInfo getPortalInfo(Entity original, ServerLevel destWorld, Function<ServerLevel, PortalInfo> defaultPortalInfo) {
                return new PortalInfo(target, Vec3.ZERO, original.getYRot(), original.getXRot());
            }

            @Override
            public boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) {
                return false;
            }
        });
    }

    @Nullable
    public static BlockPos arrivalPosition(ServerLevel destination, Entity entity) {
        BlockPos origin = entity.blockPosition();
        WorldBorder border = destination.getWorldBorder();
        if (!border.isWithinBounds(origin)) {
            origin = new BlockPos((int) border.getCenterX(), origin.getY(), (int) border.getCenterZ());
        }
        int min = destination.getMinBuildHeight() + 1;
        int max = destination.getMaxBuildHeight() - (int) Math.ceil(entity.getBbHeight());
        int preferred = Mth.clamp(origin.getY(), min, max);
        for (int radius = 0; radius <= 2; radius++) {
            for (int dy = 0; dy <= max - min; dy++) {
                for (int y : dy == 0 ? new int[]{preferred} : new int[]{preferred + dy, preferred - dy}) {
                    if (y < min || y > max) continue;
                    for (int x = -radius; x <= radius; x++) {
                        for (int z = -radius; z <= radius; z++) {
                            if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                            BlockPos candidate = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
                            if (safePosition(destination, candidate, entity)) return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static boolean safePosition(ServerLevel level, BlockPos pos, Entity entity) {
        Vec3 arrival = Vec3.atBottomCenterOf(pos);
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(arrival);
        for (Entity passenger : entity.getIndirectPassengers()) {
            double offset = Math.max(entity.getBbHeight(), passenger.getY() - entity.getY());
            box = box.minmax(passenger.getDimensions(passenger.getPose()).makeBoundingBox(arrival.add(0, offset, 0)));
        }
        if (box.minY <= level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (BlockPos target : BlockPos.betweenClosed(
                (int) Math.floor(box.minX), pos.getY() - 1, (int) Math.floor(box.minZ),
                (int) Math.ceil(box.maxX) - 1, (int) Math.ceil(box.maxY) - 1, (int) Math.ceil(box.maxZ) - 1)) {
            var state = level.getBlockState(target);
            if (!state.getFluidState().isEmpty() || hazardous(state)) return false;
            if (target.getY() < pos.getY() && !state.isFaceSturdy(level, target, Direction.UP)) return false;
        }
        return level.noCollision(box);
    }

    private static boolean hazardous(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.NETHER_PORTAL);
    }
}
