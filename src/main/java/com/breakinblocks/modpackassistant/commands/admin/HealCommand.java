package com.breakinblocks.modpackassistant.commands.admin;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public final class HealCommand {
    private HealCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("heal")
                .requires(MAPermissions.GAMEMASTER)
                .executes(context -> heal(context.getSource(), CommandResults.player(context.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> heal(context.getSource(), EntityArgument.getPlayer(context, "player"))));
    }

    private static int heal(CommandSourceStack source, ServerPlayer target) {
        target.setHealth(target.getMaxHealth());
        target.getFoodData().eat(20, 20.0F);
        if (source.getEntity() != target) {
            target.sendSystemMessage(Messages.HEAL_TARGET.get(source.getDisplayName()));
        }
        return CommandResults.success(source, Messages.HEAL_DONE.get(target.getDisplayName()));
    }
}
