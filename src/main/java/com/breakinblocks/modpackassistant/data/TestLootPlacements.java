package com.breakinblocks.modpackassistant.data;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class TestLootPlacements extends SavedData {
    public static final SavedDataType<TestLootPlacements> TYPE = new SavedDataType<>(
            ModpackAssistant.id("test_loot"),
            TestLootPlacements::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension").forGetter(data -> Optional.ofNullable(data.dimension)),
                    BlockPos.CODEC.listOf().optionalFieldOf("positions", List.of()).forGetter(data -> data.positions)
            ).apply(instance, TestLootPlacements::new)));

    @Nullable
    private ResourceKey<Level> dimension;
    private final List<BlockPos> positions = new ArrayList<>();

    public TestLootPlacements() {
    }

    private TestLootPlacements(Optional<ResourceKey<Level>> dimension, List<BlockPos> positions) {
        this.dimension = dimension.orElse(null);
        this.positions.addAll(positions);
    }

    public static TestLootPlacements get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    @Nullable
    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public List<BlockPos> positions() {
        return Collections.unmodifiableList(positions);
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    public void record(ResourceKey<Level> dimension, List<BlockPos> placed) {
        this.dimension = dimension;
        positions.clear();
        positions.addAll(placed);
        setDirty();
    }

    public void clear() {
        dimension = null;
        positions.clear();
        setDirty();
    }
}
