package com.breakinblocks.modpackassistant.commands;

import com.breakinblocks.modpackassistant.config.MAConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.function.Predicate;

public final class MAPermissions {
    public static final Predicate<CommandSourceStack> GAMEMASTER = source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS);
    public static final Predicate<CommandSourceStack> ITEM_INSPECTION = source -> source.hasPermission(MAConfig.itemInspectionPermission());

    private MAPermissions() {
    }
}
