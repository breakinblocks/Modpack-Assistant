package com.breakinblocks.modpackassistant.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestLootPlacements extends SavedData {
    private static final String NAME = "modpackassistant_test_loot";
    private static final Factory<TestLootPlacements> FACTORY = new Factory<>(TestLootPlacements::new, TestLootPlacements::load, null);

    @Nullable
    private ResourceKey<Level> dimension;
    private final List<BlockPos> positions = new ArrayList<>();

    public static TestLootPlacements get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static TestLootPlacements load(CompoundTag tag, HolderLookup.Provider registries) {
        TestLootPlacements data = new TestLootPlacements();
        if (tag.contains("dimension", Tag.TAG_STRING)) {
            data.dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension")));
        }
        ListTag list = tag.getList("positions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            NbtUtils.readBlockPos(list.getCompound(i), "pos").ifPresent(data.positions::add);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (dimension != null) {
            tag.putString("dimension", dimension.location().toString());
        }
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            CompoundTag entry = new CompoundTag();
            entry.put("pos", NbtUtils.writeBlockPos(pos));
            list.add(entry);
        }
        tag.put("positions", list);
        return tag;
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
