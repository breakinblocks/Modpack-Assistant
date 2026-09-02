package com.breakinblocks.modpackassistant.commands.world;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.commands.args.KillTypeArgument;
import com.breakinblocks.modpackassistant.commands.args.KillTypeArgument.KillType;
import com.breakinblocks.modpackassistant.util.MATags;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class KillCommand {
    private KillCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext buildContext) {
        return Commands.literal("kill")
                .requires(MAPermissions.GAMEMASTER)
                .then(Commands.argument("type", KillTypeArgument.killType())
                        .executes(context -> kill(context.getSource(), KillTypeArgument.get(context, "type"))))
                .then(Commands.literal("by")
                        .then(Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                                .suggests(SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES))
                                .executes(context -> killByType(context, ResourceArgument.getSummonableEntityType(context, "entity")))));
    }

    private static int kill(CommandSourceStack source, KillType type) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ServerPlayer caller = CommandResults.player(source);
        Component typeName = type.label().get();
        Component dimension = Component.literal(level.dimension().identifier().toString());
        source.sendSuccess(() -> Messages.KILL_START.get(typeName, dimension), true);

        int killed = switch (type) {
            case PLAYERS -> killPlayers(level, player -> true);
            case ME -> killPlayers(level, player -> player.getUUID().equals(caller.getUUID()));
            case ALL -> removeEntities(level, entity -> !(entity instanceof Player), true);
            case ANIMALS -> removeEntities(level, entity -> entity instanceof Animal, true);
            case MONSTERS -> removeEntities(level, entity -> entity instanceof Enemy && !(entity instanceof Player), true);
            case ITEMS -> removeEntities(level, entity -> entity instanceof ItemEntity, true);
            case XP -> removeEntities(level, entity -> entity instanceof ExperienceOrb, true);
        };
        return report(source, killed, typeName);
    }

    private static int killByType(CommandContext<CommandSourceStack> context, Holder.Reference<EntityType<?>> holder) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        CommandResults.player(source);
        EntityType<?> type = holder.value();
        Component typeName = type.getDescription();
        Component dimension = Component.literal(level.dimension().identifier().toString());
        source.sendSuccess(() -> Messages.KILL_START_BYPASS.get(typeName, dimension), true);

        int killed;
        if (type == EntityType.PLAYER) {
            killed = killPlayers(level, player -> true);
        } else {
            killed = removeEntities(level, entity -> entity.getType() == type, false);
        }
        return report(source, killed, typeName);
    }

    private static int report(CommandSourceStack source, int killed, Component typeName) {
        if (killed == 0) {
            source.sendSuccess(() -> Messages.KILL_NONE.get(typeName), true);
            return 0;
        }
        return CommandResults.broadcast(source, Messages.KILL_DONE.get(killed), killed);
    }

    private static int killPlayers(ServerLevel level, Predicate<ServerPlayer> filter) {
        int killed = 0;
        for (ServerPlayer player : new ArrayList<>(level.players())) {
            if (filter.test(player) && !player.isRemoved()) {
                player.kill(level);
                killed++;
            }
        }
        return killed;
    }

    private static int removeEntities(ServerLevel level, Predicate<Entity> filter, boolean respectProtection) {
        List<Entity> entities = new ArrayList<>();
        level.getAllEntities().forEach(entities::add);
        int removed = 0;
        for (Entity entity : entities) {
            if (entity.isRemoved() || entity instanceof Player) {
                continue;
            }
            if (respectProtection && entity.is(MATags.KILL_PROTECTED)) {
                continue;
            }
            if (filter.test(entity)) {
                entity.remove(Entity.RemovalReason.KILLED);
                removed++;
            }
        }
        return removed;
    }
}
