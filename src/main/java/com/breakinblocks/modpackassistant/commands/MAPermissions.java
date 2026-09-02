package com.breakinblocks.modpackassistant.commands;

import com.breakinblocks.modpackassistant.config.MAConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.function.Predicate;

public final class MAPermissions {
    public static final Predicate<CommandSourceStack> GAMEMASTER = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    public static final Predicate<CommandSourceStack> ITEM_INSPECTION = source ->
            source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(MAConfig.itemInspectionPermission())));

    private MAPermissions() {
    }
}
