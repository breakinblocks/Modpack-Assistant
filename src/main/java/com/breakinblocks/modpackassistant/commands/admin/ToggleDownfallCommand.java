package com.breakinblocks.modpackassistant.commands.admin;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class ToggleDownfallCommand {
    private static final int DURATION_TICKS = 6000;

    private ToggleDownfallCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("toggledownfall")
                .requires(MAPermissions.GAMEMASTER)
                .executes(context -> toggle(context.getSource()));
    }

    private static int toggle(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld.isRaining() || overworld.isThundering()) {
            server.setWeatherParameters(DURATION_TICKS, 0, false, false);
            return CommandResults.broadcast(source, Messages.WEATHER_CLEAR.get(DURATION_TICKS), 1);
        }
        server.setWeatherParameters(0, DURATION_TICKS, true, false);
        return CommandResults.broadcast(source, Messages.WEATHER_RAIN.get(DURATION_TICKS), 1);
    }
}
