package com.breakinblocks.modpackassistant.commands.args;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.StringRepresentableArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;

public final class RegistryKindArgument extends StringRepresentableArgument<RegistryKindArgument.RegistryKind> {
    public enum RegistryKind implements StringRepresentable {
        ITEM("item", BuiltInRegistries.ITEM),
        BLOCK("block", BuiltInRegistries.BLOCK),
        ENTITY("entity", BuiltInRegistries.ENTITY_TYPE),
        FLUID("fluid", BuiltInRegistries.FLUID);

        public static final Codec<RegistryKind> CODEC = StringRepresentable.fromEnum(RegistryKind::values);

        private final String name;
        private final Registry<?> registry;

        RegistryKind(String name, Registry<?> registry) {
            this.name = name;
            this.registry = registry;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public Registry<?> registry() {
            return registry;
        }
    }

    public RegistryKindArgument() {
        super(RegistryKind.CODEC, RegistryKind::values);
    }

    public static RegistryKindArgument registryKind() {
        return new RegistryKindArgument();
    }

    public static RegistryKind get(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, RegistryKind.class);
    }
}
