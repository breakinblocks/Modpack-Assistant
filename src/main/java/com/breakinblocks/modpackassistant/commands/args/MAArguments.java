package com.breakinblocks.modpackassistant.commands.args;

import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class MAArguments {
    private MAArguments() {}

    public static <E extends Enum<E> & StringRepresentable> E get(CommandContext<CommandSourceStack> context, String name, E[] values) throws CommandSyntaxException {
        String input = StringArgumentType.getString(context, name);
        for (E value : values) {
            if (value.getSerializedName().equals(input)) {
                return value;
            }
        }
        throw new SimpleCommandExceptionType(Messages.INVALID_ENUM.get(input,
                Arrays.stream(values).map(StringRepresentable::getSerializedName).collect(Collectors.joining(", ")))).create();
    }

    public static <E extends Enum<E> & StringRepresentable> CompletableFuture<Suggestions> suggest(E[] values, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Arrays.stream(values).map(StringRepresentable::getSerializedName), builder);
    }
}
