package com.breakinblocks.modpackassistant.commands.args;

import com.breakinblocks.modpackassistant.util.Messages;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.StringRepresentableArgument;
import net.minecraft.util.StringRepresentable;

public final class KillTypeArgument extends StringRepresentableArgument<KillTypeArgument.KillType> {
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

    public KillTypeArgument() {
        super(KillType.CODEC, KillType::values);
    }

    public static KillTypeArgument killType() {
        return new KillTypeArgument();
    }

    public static KillType get(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, KillType.class);
    }
}
