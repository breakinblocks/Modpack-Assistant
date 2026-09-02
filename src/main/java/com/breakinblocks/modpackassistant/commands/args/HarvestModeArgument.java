package com.breakinblocks.modpackassistant.commands.args;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.StringRepresentableArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.Nullable;

public final class HarvestModeArgument extends StringRepresentableArgument<HarvestModeArgument.HarvestMode> {
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

    public HarvestModeArgument() {
        super(HarvestMode.CODEC, HarvestMode::values);
    }

    public static HarvestModeArgument harvestMode() {
        return new HarvestModeArgument();
    }

    public static HarvestMode get(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, HarvestMode.class);
    }
}
