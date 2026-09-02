package com.breakinblocks.modpackassistant.commands.world;

import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.jobs.RunScheduler;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class CancelCommand {
    private CancelCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("cancel")
                .requires(MAPermissions.GAMEMASTER)
                .executes(context -> RunScheduler.cancel(context.getSource()) ? 1 : 0);
    }
}
