package com.breakinblocks.modpackassistant.commands;

import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CommandResults {
    public static final SimpleCommandExceptionType PLAYER_ONLY = new SimpleCommandExceptionType(Messages.PLAYER_ONLY.get());

    private CommandResults() {
    }

    public static int fail(CommandSourceStack source, Component reason) {
        source.sendFailure(reason);
        return 0;
    }

    public static int success(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
        return 1;
    }

    public static int success(CommandSourceStack source, Component message, int count) {
        source.sendSuccess(() -> message, false);
        return atLeastOne(count);
    }

    public static int broadcast(CommandSourceStack source, Component message, int count) {
        source.sendSuccess(() -> message, true);
        return atLeastOne(count);
    }

    public static int atLeastOne(int count) {
        return Math.max(1, count);
    }

    public static ServerPlayer player(CommandSourceStack source) throws CommandSyntaxException {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        throw PLAYER_ONLY.create();
    }
}
