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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CopyCommand {
    private CopyCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("copy")
                .requires(MAPermissions.ITEM_INSPECTION)
                .then(Commands.argument("source", StringArgumentType.word())
                        .suggests(ItemSource::suggest)
                        .executes(context -> copy(context.getSource(), StringArgumentType.getString(context, "source"), null))
                        .then(Commands.argument("format", StringArgumentType.word())
                                .suggests(OutputFormat::suggest)
                                .executes(context -> copy(context.getSource(), StringArgumentType.getString(context, "source"), StringArgumentType.getString(context, "format")))));
    }

    private static int copy(CommandSourceStack source, String sourceName, @Nullable String formatName) throws CommandSyntaxException {
        ItemSource itemSource = ItemSource.byName(sourceName);
        if (itemSource == null) {
            return CommandResults.fail(source, Messages.INVALID_SELECTOR.get(sourceName, ItemSource.names()));
        }
        OutputFormat format = formatName == null ? OutputFormat.PLAIN : OutputFormat.byName(formatName);
        if (format == null) {
            return CommandResults.fail(source, Messages.INVALID_FORMAT.get(formatName, OutputFormat.names()));
        }
        ServerPlayer player = CommandResults.player(source);
        List<ItemStack> items = itemSource.collect(player);
        if (items.isEmpty()) {
            return CommandResults.fail(source, Messages.NO_ITEM.get(player.getName()));
        }

        String output = format.write(items, source.registryAccess());
        if (MANetworking.sendClipboard(player, output)) {
            source.sendSuccess(() -> Messages.CLIPBOARD_COPIED.get().withStyle(ChatFormatting.YELLOW), false);
        } else {
            source.sendSuccess(() -> PrintCommand.copyable(output, ChatFormatting.YELLOW, Messages.CLIPBOARD_CLICK.get()), false);
        }
        return items.size();
    }
}
