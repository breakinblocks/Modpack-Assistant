package com.breakinblocks.modpackassistant.commands.args;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.StringRepresentable;

public final class ClearKeepArgument {
    public enum ClearKeep implements StringRepresentable {
        ORES("ores"),
        ORES_AND_MODDED("ores_and_modded"),
        NOTHING("nothing");

        public static final Codec<ClearKeep> CODEC = StringRepresentable.fromEnum(ClearKeep::values);

        private final String name;

        ClearKeep(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    private ClearKeepArgument() {}

    public static StringArgumentType clearKeep() {
        return StringArgumentType.word();
    }

    public static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return MAArguments.suggest(ClearKeep.values(), builder);
    }

    public static ClearKeep get(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return MAArguments.get(context, name, ClearKeep.values());
    }
}
