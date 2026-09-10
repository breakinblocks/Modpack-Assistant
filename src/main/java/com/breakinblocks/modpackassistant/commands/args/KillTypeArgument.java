package com.breakinblocks.modpackassistant.commands.args;

import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.StringRepresentable;

public final class KillTypeArgument {
    public enum KillType implements StringRepresentable {
        ALL("all", Messages.KILL_TYPE_ALL),
        ANIMALS("animals", Messages.KILL_TYPE_ANIMALS),
        MONSTERS("monsters", Messages.KILL_TYPE_MONSTERS),
        ITEMS("items", Messages.KILL_TYPE_ITEMS),
        XP("xp", Messages.KILL_TYPE_XP),
        PLAYERS("players", Messages.KILL_TYPE_PLAYERS),
        ME("me", Messages.KILL_TYPE_ME);

        public static final Codec<KillType> CODEC = StringRepresentable.fromEnum(KillType::values);

        private final String name;
        private final Messages.Msg label;

        KillType(String name, Messages.Msg label) {
            this.name = name;
            this.label = label;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public Messages.Msg label() {
            return label;
        }
    }

    private KillTypeArgument() {}

    public static StringArgumentType killType() {
        return StringArgumentType.word();
    }

    public static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return MAArguments.suggest(KillType.values(), builder);
    }

    public static KillType get(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return MAArguments.get(context, name, KillType.values());
    }
}
