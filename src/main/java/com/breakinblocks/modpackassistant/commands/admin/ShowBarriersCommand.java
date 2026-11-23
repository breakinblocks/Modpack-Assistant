package com.breakinblocks.modpackassistant.commands.admin;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.net.MANetworking;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class ShowBarriersCommand {
    private ShowBarriersCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("showBarriers")
                .requires(MAPermissions.GAMEMASTER)
                .then(Commands.literal("on").executes(context -> set(context, true)))
                .then(Commands.literal("off").executes(context -> set(context, false)));
    }

    private static int set(CommandContext<CommandSourceStack> context, boolean shown) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandResults.player(source);
        if (!MANetworking.sendShowBarriers(player, shown)) {
            return CommandResults.fail(source, Messages.SHOWBARRIERS_NO_CLIENT.get(player.getName()));
        }
        return CommandResults.success(source, shown ? Messages.SHOWBARRIERS_ON.get() : Messages.SHOWBARRIERS_OFF.get());
    }
}
