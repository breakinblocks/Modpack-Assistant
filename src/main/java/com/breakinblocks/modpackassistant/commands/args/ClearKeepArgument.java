package com.breakinblocks.modpackassistant.commands.args;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.StringRepresentableArgument;
import net.minecraft.util.StringRepresentable;

public final class ClearKeepArgument extends StringRepresentableArgument<ClearKeepArgument.ClearKeep> {
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

    public ClearKeepArgument() {
        super(ClearKeep.CODEC, ClearKeep::values);
    }

    public static ClearKeepArgument clearKeep() {
        return new ClearKeepArgument();
    }

    public static ClearKeep get(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, ClearKeep.class);
    }
}
