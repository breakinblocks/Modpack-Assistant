package com.breakinblocks.modpackassistant.commands.admin;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class NightVisionCommand {
    private static final int DURATION = 9_999_999;
    private static final int AMPLIFIER = 3;

    private NightVisionCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("nightvision")
                .requires(MAPermissions.GAMEMASTER)
                .executes(context -> toggle(context.getSource(), CommandResults.player(context.getSource())));
    }

    private static int toggle(CommandSourceStack source, ServerPlayer player) {
        if (player.hasEffect(MobEffects.NIGHT_VISION)) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            return CommandResults.success(source, Messages.NIGHTVISION_OFF.get());
        }
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION, AMPLIFIER, false, false, false));
        return CommandResults.success(source, Messages.NIGHTVISION_ON.get());
    }
}
