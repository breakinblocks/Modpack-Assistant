package com.breakinblocks.modpackassistant.datagen;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.util.MATags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public final class EntityTagProvider extends EntityTypeTagsProvider {
    public EntityTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookup, ModpackAssistant.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(MATags.KILL_PROTECTED)
                .add(EntityType.MINECART,
                        EntityType.CHEST_MINECART,
                        EntityType.FURNACE_MINECART,
                        EntityType.HOPPER_MINECART,
                        EntityType.TNT_MINECART,
                        EntityType.COMMAND_BLOCK_MINECART,
                        EntityType.SPAWNER_MINECART)
                .addOptional(ResourceLocation.parse("aeronautics:propeller_bearing_contraption"))
                .addOptional(ResourceLocation.parse("create:contraption"))
                .addOptional(ResourceLocation.parse("create:stationary_contraption"))
                .addOptional(ResourceLocation.parse("create:gantry_contraption"))
                .addOptional(ResourceLocation.parse("create:carriage_contraption"));
    }
}
