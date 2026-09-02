package com.breakinblocks.modpackassistant.commands.admin;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class RepairCommand {
    private RepairCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("repair")
                .requires(MAPermissions.GAMEMASTER)
                .executes(context -> repair(context.getSource(), CommandResults.player(context.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> repair(context.getSource(), EntityArgument.getPlayer(context, "player"))));
    }

    private static int repair(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ItemStack stack = EnchantCommand.heldItem(target, source);
        Component itemName = stack.getHoverName();
        if (!stack.isDamageableItem()) {
            return CommandResults.fail(source, Messages.REPAIR_NOT_DAMAGEABLE.get(itemName));
        }
        stack.setDamageValue(0);
        if (source.getEntity() != target) {
            target.sendSystemMessage(Messages.REPAIR_TARGET.get(source.getDisplayName(), itemName));
        }
        return CommandResults.success(source, Messages.REPAIR_DONE.get(itemName));
    }
}
