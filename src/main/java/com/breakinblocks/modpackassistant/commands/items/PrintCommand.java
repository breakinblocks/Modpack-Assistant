package com.breakinblocks.modpackassistant.commands.items;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.commands.MAPermissions;
import com.breakinblocks.modpackassistant.net.MANetworking;
import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class PrintCommand {
    private PrintCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("print")
                .requires(MAPermissions.ITEM_INSPECTION)
                .then(Commands.argument("source", StringArgumentType.word())
                        .suggests(ItemSource::suggest)
                        .executes(context -> print(context.getSource(), StringArgumentType.getString(context, "source"), false)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildHand() {
        return Commands.literal("hand")
                .requires(MAPermissions.ITEM_INSPECTION)
                .executes(context -> print(context.getSource(), ItemSource.HAND.sourceName(), true));
    }

    private static int print(CommandSourceStack source, String sourceName, boolean copy) throws CommandSyntaxException {
        ItemSource itemSource = ItemSource.byName(sourceName);
        if (itemSource == null) {
            return CommandResults.fail(source, Messages.INVALID_SELECTOR.get(sourceName, ItemSource.names()));
        }
        ServerPlayer player = CommandResults.player(source);
        HolderLookup.Provider lookup = source.registryAccess();
        List<ItemStack> items = itemSource.collect(player);
        if (items.isEmpty()) {
            return CommandResults.fail(source, Messages.NO_ITEM.get(player.getName()));
        }

        for (ItemStack stack : items) {
            String value = ItemStrings.countedGiveString(stack, lookup);
            source.sendSuccess(() -> copyable(value, ChatFormatting.YELLOW, Messages.CLIPBOARD_CLICK.get()), false);
            if (copy) {
                MANetworking.sendClipboard(player, ItemStrings.giveString(stack, lookup));
            }
            stack.tags().forEach(tag -> {
                String tagString = "#" + tag.location();
                source.sendSuccess(() -> Component.literal("- ").append(copyable(tagString, ChatFormatting.RED, Messages.PRINT_TAG_CLICK.get())), false);
            });
        }
        return items.size();
    }

    public static MutableComponent copyable(String value, ChatFormatting color, Component hover) {
        return Component.literal(value).withStyle(Style.EMPTY
                .withColor(color)
                .withClickEvent(new ClickEvent.CopyToClipboard(value))
                .withHoverEvent(new HoverEvent.ShowText(hover)));
    }
}
