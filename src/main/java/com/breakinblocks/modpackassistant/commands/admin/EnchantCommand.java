package com.breakinblocks.modpackassistant.commands.admin;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class EnchantCommand {
    public static final int MAX_LEVEL = 255;

    private EnchantCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return Commands.literal("enchant")
                .requires(MAPermissions.GAMEMASTER)
                .then(Commands.literal("add")
                        .then(Commands.argument("enchantment", ResourceArgument.resource(context, Registries.ENCHANTMENT))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, MAX_LEVEL))
                                        .executes(ctx -> add(ctx, ResourceArgument.getEnchantment(ctx, "enchantment"), IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("enchantment", ResourceArgument.resource(context, Registries.ENCHANTMENT))
                                .executes(ctx -> remove(ctx, ResourceArgument.getEnchantment(ctx, "enchantment")))));
    }

    public static ItemStack heldItem(ServerPlayer player, CommandSourceStack source) throws CommandSyntaxException {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            throw new SimpleCommandExceptionType(Messages.NO_ITEM.get(player.getName())).create();
        }
        return stack;
    }

    private static int add(CommandContext<CommandSourceStack> context, Holder.Reference<Enchantment> enchantment, int level) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandResults.player(source);
        ItemStack stack = heldItem(player, source);
        Component itemName = stack.getHoverName();

        if (level == 0) {
            return remove(context, enchantment);
        }

        ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (!stack.supportsEnchantment(enchantment)) {
            return CommandResults.fail(source, Messages.ENCHANT_INCOMPATIBLE.get(itemName, Enchantment.getFullname(enchantment, level), itemName));
        }
        for (Holder<Enchantment> other : existing.keySet()) {
            if (!other.equals(enchantment) && !Enchantment.areCompatible(other, enchantment)) {
                return CommandResults.fail(source, Messages.ENCHANT_INCOMPATIBLE.get(itemName, Enchantment.getFullname(enchantment, level), Enchantment.getFullname(other, existing.getLevel(other))));
            }
        }

        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(enchantment, level));
        return CommandResults.success(source, Messages.ENCHANT_DONE.get(itemName, Enchantment.getFullname(enchantment, level)));
    }

    private static int remove(CommandContext<CommandSourceStack> context, Holder.Reference<Enchantment> enchantment) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandResults.player(source);
        ItemStack stack = heldItem(player, source);
        Component itemName = stack.getHoverName();

        ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        int level = existing.getLevel(enchantment);
        if (level <= 0) {
            return CommandResults.fail(source, Messages.ENCHANT_MISSING.get(itemName, Enchantment.getFullname(enchantment, 1)));
        }

        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(enchantment, 0));
        return CommandResults.success(source, Messages.ENCHANT_REMOVED.get(Enchantment.getFullname(enchantment, level), itemName));
    }
}
