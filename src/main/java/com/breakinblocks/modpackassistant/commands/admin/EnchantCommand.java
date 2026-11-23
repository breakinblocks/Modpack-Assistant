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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.Items;

import java.util.Map;

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
                                        .executes(ctx -> add(ctx, ResourceArgument.getResource(ctx, "enchantment", Registries.ENCHANTMENT).value(), IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("enchantment", ResourceArgument.resource(context, Registries.ENCHANTMENT))
                                .executes(ctx -> remove(ctx, ResourceArgument.getResource(ctx, "enchantment", Registries.ENCHANTMENT).value()))));
    }

    public static ItemStack heldItem(ServerPlayer player, CommandSourceStack source) throws CommandSyntaxException {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            throw new SimpleCommandExceptionType(Messages.NO_ITEM.get(player.getName())).create();
        }
        return stack;
    }

    private static int add(CommandContext<CommandSourceStack> context, Enchantment enchantment, int level) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandResults.player(source);
        ItemStack stack = heldItem(player, source);
        Component itemName = stack.getHoverName();

        if (level == 0) {
            return remove(context, enchantment);
        }

        Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(stack);
        if (!stack.is(Items.ENCHANTED_BOOK) && !enchantment.canEnchant(stack)) {
            return CommandResults.fail(source, Messages.ENCHANT_INCOMPATIBLE.get(itemName, enchantment.getFullname(level), itemName));
        }
        for (Map.Entry<Enchantment, Integer> other : existing.entrySet()) {
            if (other.getKey() != enchantment && !enchantment.isCompatibleWith(other.getKey())) {
                return CommandResults.fail(source, Messages.ENCHANT_INCOMPATIBLE.get(itemName, enchantment.getFullname(level), other.getKey().getFullname(other.getValue())));
            }
        }

        existing.put(enchantment, level);
        EnchantmentHelper.setEnchantments(existing, stack);
        return CommandResults.success(source, Messages.ENCHANT_DONE.get(itemName, enchantment.getFullname(level)));
    }

    private static int remove(CommandContext<CommandSourceStack> context, Enchantment enchantment) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = CommandResults.player(source);
        ItemStack stack = heldItem(player, source);
        Component itemName = stack.getHoverName();

        Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(stack);
        int level = existing.getOrDefault(enchantment, 0);
        if (level <= 0) {
            return CommandResults.fail(source, Messages.ENCHANT_MISSING.get(itemName, enchantment.getFullname(1)));
        }

        existing.remove(enchantment);
        EnchantmentHelper.setEnchantments(existing, stack);
        return CommandResults.success(source, Messages.ENCHANT_REMOVED.get(enchantment.getFullname(level), itemName));
    }
}
