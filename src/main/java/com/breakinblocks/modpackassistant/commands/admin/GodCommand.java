package com.breakinblocks.modpackassistant.commands.admin;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public final class GodCommand {
    private GodCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("god")
                .requires(MAPermissions.GAMEMASTER)
                .executes(context -> toggle(context.getSource(), CommandResults.player(context.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> toggle(context.getSource(), EntityArgument.getPlayer(context, "player"))));
    }

    private static int toggle(CommandSourceStack source, ServerPlayer target) {
        boolean enabled = !target.isInvulnerable();
        target.setInvulnerable(enabled);
        if (source.getEntity() != target) {
            target.sendSystemMessage((enabled ? Messages.GOD_TARGET_ON : Messages.GOD_TARGET_OFF).get(source.getDisplayName()));
        }
        return CommandResults.success(source, (enabled ? Messages.GOD_ON : Messages.GOD_OFF).get(target.getDisplayName()));
    }
}
