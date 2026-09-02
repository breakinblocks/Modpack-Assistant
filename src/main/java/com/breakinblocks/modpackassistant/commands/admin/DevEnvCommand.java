package com.breakinblocks.modpackassistant.commands.admin;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.gamerules.GameRules;

public final class DevEnvCommand {
    private static final long NOON = 6000L;

    private DevEnvCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("devenv")
                .requires(MAPermissions.GAMEMASTER)
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> set(context.getSource(), BoolArgumentType.getBool(context, "enabled"))));
    }

    private static int set(CommandSourceStack source, boolean enabled) {
        MinecraftServer server = source.getServer();
        GameRules rules = server.getGameRules();
        rules.set(GameRules.ADVANCE_TIME, !enabled, server);
        rules.set(GameRules.SPAWN_MOBS, !enabled, server);
        rules.set(GameRules.ADVANCE_WEATHER, !enabled, server);
        if (enabled) {
            ServerClockManager clocks = server.clockManager();
            for (Holder<WorldClock> clock : server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).listElements().toList()) {
                clocks.setTotalTicks(clock, NOON);
            }
            for (ServerLevel level : server.getAllLevels()) {
                level.updateSkyBrightness();
            }
            return CommandResults.broadcast(source, Messages.DEVENV_ON.get(), 1);
        }
        return CommandResults.broadcast(source, Messages.DEVENV_OFF.get(), 1);
    }
}
