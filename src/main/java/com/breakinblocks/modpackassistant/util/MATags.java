package com.breakinblocks.modpackassistant.util;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public final class MATags {
    public static final TagKey<EntityType<?>> KILL_PROTECTED = TagKey.create(Registries.ENTITY_TYPE, ModpackAssistant.id("kill_protected"));

    private MATags() {
    }
}
