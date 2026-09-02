package com.breakinblocks.modpackassistant.datagen;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.util.MATags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagEntry;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.CompletableFuture;

public final class EntityTagProvider extends EntityTypeTagsProvider {
    public EntityTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, ModpackAssistant.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        TagAppender<EntityType<?>, EntityType<?>> appender = tag(MATags.KILL_PROTECTED)
                .add(EntityType.MINECART,
                        EntityType.CHEST_MINECART,
                        EntityType.FURNACE_MINECART,
                        EntityType.HOPPER_MINECART,
                        EntityType.TNT_MINECART,
                        EntityType.COMMAND_BLOCK_MINECART,
                        EntityType.SPAWNER_MINECART);
        appender.add(TagEntry.optionalElement(Identifier.parse("aeronautics:propeller_bearing_contraption")));
        appender.add(TagEntry.optionalElement(Identifier.parse("create:contraption")));
        appender.add(TagEntry.optionalElement(Identifier.parse("create:stationary_contraption")));
        appender.add(TagEntry.optionalElement(Identifier.parse("create:gantry_contraption")));
        appender.add(TagEntry.optionalElement(Identifier.parse("create:carriage_contraption")));
    }
}
