package com.breakinblocks.modpackassistant.commands.admin;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpswordCommand {
    public static final int LEVEL = 255;
    private static final List<Enchantment> ENCHANTMENTS = List.of(
            Enchantments.SHARPNESS,
            Enchantments.UNBREAKING,
            Enchantments.BANE_OF_ARTHROPODS,
            Enchantments.SMITE,
            Enchantments.SWEEPING_EDGE
    );

    private OpswordCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("opsword")
                .requires(MAPermissions.GAMEMASTER)
                .executes(context -> give(context.getSource()));
    }

    private static int give(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = CommandResults.player(source);
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.setHoverName(Messages.OPSWORD_NAME.get());
        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        for (Enchantment enchantment : ENCHANTMENTS) {
            enchantments.put(enchantment, LEVEL);
        }
        EnchantmentHelper.setEnchantments(enchantments, sword);

        if (!player.getInventory().add(sword)) {
            ItemEntity drop = player.drop(sword, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setTarget(player.getUUID());
            }
        }
        return CommandResults.success(source, Messages.OPSWORD_GIVEN.get());
    }
}
