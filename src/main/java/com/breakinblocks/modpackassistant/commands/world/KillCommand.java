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

import java.util.concurrent.atomic.AtomicInteger;
import com.breakinblocks.modpackassistant.jobs.EntityIndex;
import com.breakinblocks.modpackassistant.jobs.Run;
import com.breakinblocks.modpackassistant.jobs.RunScheduler;
import java.util.function.Predicate;

public final class KillCommand {
    private KillCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext buildContext) {
        return Commands.literal("kill")
                .requires(MAPermissions.GAMEMASTER)
                .then(Commands.argument("type", KillTypeArgument.killType()).suggests(KillTypeArgument::suggest)
                        .executes(context -> kill(context.getSource(), KillTypeArgument.get(context, "type"))))
                .then(Commands.literal("by")
                        .then(Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                                .suggests(SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES))
                                .executes(context -> killByType(context, ResourceArgument.getSummonableEntityType(context, "entity")))));
    }

    private static int kill(CommandSourceStack source, KillType type) throws CommandSyntaxException {
        ServerPlayer caller = type == KillType.ME ? CommandResults.player(source) : null;
        Predicate<Entity> filter = switch (type) {
            case PLAYERS -> entity -> entity instanceof ServerPlayer;
            case ME -> entity -> entity == caller;
            case ALL -> entity -> !(entity instanceof Player);
            case ANIMALS -> entity -> entity instanceof Animal && !(entity instanceof Player);
            case MONSTERS -> entity -> entity instanceof Enemy && !(entity instanceof Player);
            case ITEMS -> entity -> entity instanceof ItemEntity;
            case XP -> entity -> entity instanceof ExperienceOrb;
        };
        return schedule(source, filter, true, type.label().get());
    }

    private static int killByType(CommandContext<CommandSourceStack> context, Holder.Reference<EntityType<?>> holder) {
        EntityType<?> type = holder.value();
        return schedule(context.getSource(), entity -> entity.getType() == type, false, type.getDescription());
    }

    private static int schedule(CommandSourceStack source, Predicate<Entity> filter, boolean respectProtection, Component typeName) {
        ServerLevel level = source.getLevel();
        EntityIndex.Cursor cursor = EntityIndex.cursor(level);
        AtomicInteger removed = new AtomicInteger();
        Run run = new Run(source, "entity removal", level.dimension());
        run.repeat(() -> cursor.step(128, entity -> {
            if (!filter.test(entity) || respectProtection && entity.is(MATags.KILL_PROTECTED)) return;
            if (entity instanceof ServerPlayer player) {
                if (!player.isAlive()) return;
                player.kill(level);
            } else {
                entity.remove(Entity.RemovalReason.KILLED);
            }
            removed.incrementAndGet();
        }));
        run.onComplete(finished -> finished.message(removed.get() == 0
                ? Messages.KILL_NONE.get(typeName) : Messages.KILL_DONE.get(removed.get())));
        if (!RunScheduler.tryStart(run)) return 0;
        run.message((respectProtection ? Messages.KILL_START : Messages.KILL_START_BYPASS)
                .get(typeName, level.dimension().identifier()));
        return 1;
    }
}
