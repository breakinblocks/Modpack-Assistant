package com.breakinblocks.modpackassistant.datagen;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.util.MATags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.entity.EntityTypeIds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;

public final class EntityTagProvider extends EntityTypeTagsProvider {
    public EntityTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, ModpackAssistant.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        TagAppender<EntityType<?>> appender = tag(MATags.KILL_PROTECTED)
                .add(EntityTypeIds.MINECART,
                        EntityTypeIds.CHEST_MINECART,
                        EntityTypeIds.FURNACE_MINECART,
                        EntityTypeIds.HOPPER_MINECART,
                        EntityTypeIds.TNT_MINECART,
                        EntityTypeIds.COMMAND_BLOCK_MINECART,
                        EntityTypeIds.SPAWNER_MINECART);
        appender.addOptional(optional("aeronautics:propeller_bearing_contraption"));
        appender.addOptional(optional("create:contraption"));
        appender.addOptional(optional("create:stationary_contraption"));
        appender.addOptional(optional("create:gantry_contraption"));
        appender.addOptional(optional("create:carriage_contraption"));
    }

    private static ResourceKey<EntityType<?>> optional(String id) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.parse(id));
    }
}
