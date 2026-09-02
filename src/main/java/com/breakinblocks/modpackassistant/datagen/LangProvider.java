package com.breakinblocks.modpackassistant.datagen;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.util.Messages;
import com.breakinblocks.modpackassistant.util.RomanNumerals;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public final class LangProvider extends LanguageProvider {
    public static final int MAX_ENCHANT_LEVEL = 255;

    public LangProvider(PackOutput output) {
        super(output, ModpackAssistant.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        Messages.all().forEach(this::add);
        for (int level = 11; level <= MAX_ENCHANT_LEVEL; level++) {
            add("enchantment.level." + level, RomanNumerals.of(level));
        }
    }
}
