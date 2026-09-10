package com.breakinblocks.modpackassistant.commands.args;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;

public final class RegistryKindArgument {
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

    private RegistryKindArgument() {}

    public static StringArgumentType registryKind() {
        return StringArgumentType.word();
    }

    public static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return MAArguments.suggest(RegistryKind.values(), builder);
    }

    public static RegistryKind get(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return MAArguments.get(context, name, RegistryKind.values());
    }
}
