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

public final class ReportFormatArgument {
    public enum ReportFormat implements StringRepresentable {
        JSON("json"),
        CSV("csv");

        public static final Codec<ReportFormat> CODEC = StringRepresentable.fromEnum(ReportFormat::values);

        private final String name;

        ReportFormat(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    private ReportFormatArgument() {}

    public static StringArgumentType reportFormat() {
        return StringArgumentType.word();
    }

    public static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return MAArguments.suggest(ReportFormat.values(), builder);
    }

    public static ReportFormat get(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return MAArguments.get(context, name, ReportFormat.values());
    }
}
