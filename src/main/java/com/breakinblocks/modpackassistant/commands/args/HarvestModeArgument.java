package com.breakinblocks.modpackassistant.commands.args;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.Nullable;

public final class HarvestModeArgument {
    public enum HarvestMode implements StringRepresentable {
        PLAIN("plain", null, 0),
        SILK_TOUCH("silk_touch", Enchantments.SILK_TOUCH, 1),
        FORTUNE_1("fortune_1", Enchantments.FORTUNE, 1),
        FORTUNE_2("fortune_2", Enchantments.FORTUNE, 2),
        FORTUNE_3("fortune_3", Enchantments.FORTUNE, 3);

        public static final Codec<HarvestMode> CODEC = StringRepresentable.fromEnum(HarvestMode::values);

        private final String name;
        @Nullable
        private final ResourceKey<Enchantment> enchantment;
        private final int level;

        HarvestMode(String name, @Nullable ResourceKey<Enchantment> enchantment, int level) {
            this.name = name;
            this.enchantment = enchantment;
            this.level = level;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        @Nullable
        public ResourceKey<Enchantment> enchantment() {
            return enchantment;
        }

        public int level() {
            return level;
        }
    }

    private HarvestModeArgument() {}

    public static StringArgumentType harvestMode() {
        return StringArgumentType.word();
    }

    public static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return MAArguments.suggest(HarvestMode.values(), builder);
    }

    public static HarvestMode get(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return MAArguments.get(context, name, HarvestMode.values());
    }
}
