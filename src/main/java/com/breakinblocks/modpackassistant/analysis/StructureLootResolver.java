package com.breakinblocks.modpackassistant.analysis;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class StructureLootResolver {
    public record Found(ResourceKey<LootTable> table, boolean confirmed) {
    }

    public record Result(List<Found> tables, List<String> tried) {
        public boolean isEmpty() {
            return tables.isEmpty();
        }
    }

    private StructureLootResolver() {
    }

    public static Result resolve(ServerLevel level, Holder.Reference<Structure> structure) {
        List<String> tried = new ArrayList<>();
        Set<ResourceLocation> templates = new LinkedHashSet<>();
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());

        JsonElement encoded = Structure.DIRECT_CODEC.encodeStart(ops, structure.value()).result().orElse(null);
        if (encoded != null && encoded.isJsonObject() && encoded.getAsJsonObject().has("start_pool")) {
            tried.add("template pools");
            Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
            Set<ResourceLocation> visitedPools = new LinkedHashSet<>();
            walkPool(level, pools, ops, ResourceLocation.parse(encoded.getAsJsonObject().get("start_pool").getAsString()), visitedPools, templates);
        }

        Set<ResourceKey<LootTable>> confirmed = new LinkedHashSet<>();
        if (!templates.isEmpty()) {
            tried.add("template block entities (" + templates.size() + " templates)");
            for (ResourceLocation id : templates) {
                Optional<StructureTemplate> template = level.getStructureManager().get(id);
                template.ifPresent(value -> collectLootTables(value, confirmed));
            }
        }

        List<Found> found = new ArrayList<>();
        confirmed.forEach(key -> found.add(new Found(key, true)));
        if (found.isEmpty()) {
            tried.add("loot table ids matching the structure path");
            ResourceLocation key = structure.key().location();
            for (ResourceLocation tableId : level.getServer().reloadableRegistries().getKeys(Registries.LOOT_TABLE)) {
                if (tableId.getNamespace().equals(key.getNamespace()) && tableId.getPath().contains(key.getPath())) {
                    found.add(new Found(ResourceKey.create(Registries.LOOT_TABLE, tableId), false));
                }
            }
        }
        return new Result(found, tried);
    }

    private static void walkPool(ServerLevel level, Registry<StructureTemplatePool> pools, DynamicOps<JsonElement> ops, ResourceLocation poolId, Set<ResourceLocation> visited, Set<ResourceLocation> templates) {
        if (!visited.add(poolId)) {
            return;
        }
        StructureTemplatePool pool = pools.get(poolId);
        if (pool == null) {
            return;
        }
        JsonElement encoded = StructureTemplatePool.DIRECT_CODEC.encodeStart(ops, pool).result().orElse(null);
        if (encoded == null || !encoded.isJsonObject()) {
            return;
        }
        JsonObject object = encoded.getAsJsonObject();
        if (object.has("fallback")) {
            walkPool(level, pools, ops, ResourceLocation.parse(object.get("fallback").getAsString()), visited, templates);
        }
        if (object.has("elements")) {
            for (JsonElement weighted : object.getAsJsonArray("elements")) {
                if (weighted.isJsonObject() && weighted.getAsJsonObject().has("element")) {
                    collectElement(weighted.getAsJsonObject().get("element"), templates);
                }
            }
        }
        for (ResourceLocation template : new ArrayList<>(templates)) {
            Optional<StructureTemplate> loaded = level.getStructureManager().get(template);
            loaded.ifPresent(value -> collectJigsawPools(value).forEach(next -> walkPool(level, pools, ops, next, visited, templates)));
        }
    }

    private static void collectElement(JsonElement element, Set<ResourceLocation> templates) {
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("location")) {
            templates.add(ResourceLocation.parse(object.get("location").getAsString()));
        }
        if (object.has("elements")) {
            JsonArray nested = object.getAsJsonArray("elements");
            for (JsonElement child : nested) {
                collectElement(child, templates);
            }
        }
    }

    private static List<ResourceLocation> collectJigsawPools(StructureTemplate template) {
        List<ResourceLocation> pools = new ArrayList<>();
        for (CompoundTag nbt : blockEntityTags(template)) {
            if (nbt.contains("pool", Tag.TAG_STRING)) {
                pools.add(ResourceLocation.parse(nbt.getString("pool")));
            }
        }
        return pools;
    }

    private static void collectLootTables(StructureTemplate template, Set<ResourceKey<LootTable>> tables) {
        for (CompoundTag nbt : blockEntityTags(template)) {
            if (nbt.contains("LootTable", Tag.TAG_STRING)) {
                tables.add(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(nbt.getString("LootTable"))));
            }
        }
    }

    private static List<CompoundTag> blockEntityTags(StructureTemplate template) {
        List<CompoundTag> tags = new ArrayList<>();
        CompoundTag saved = template.save(new CompoundTag());
        ListTag blocks = saved.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            if (block.contains("nbt", Tag.TAG_COMPOUND)) {
                tags.add(block.getCompound("nbt"));
            }
        }
        return tags;
    }
}
