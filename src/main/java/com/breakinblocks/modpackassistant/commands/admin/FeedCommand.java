package com.breakinblocks.modpackassistant.commands.admin;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public final class FeedCommand {
    private FeedCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("feed")
                .requires(MAPermissions.GAMEMASTER)
                .executes(context -> feed(context.getSource(), CommandResults.player(context.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> feed(context.getSource(), EntityArgument.getPlayer(context, "player"))));
    }

    private static int feed(CommandSourceStack source, ServerPlayer target) {
        target.getFoodData().eat(20, 20.0F);
        if (source.getEntity() != target) {
            target.sendSystemMessage(Messages.FEED_TARGET.get(source.getDisplayName()));
        }
        return CommandResults.success(source, Messages.FEED_DONE.get(target.getDisplayName()));
    }
}
