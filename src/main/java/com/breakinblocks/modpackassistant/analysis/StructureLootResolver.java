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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
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
        Set<Identifier> templates = new LinkedHashSet<>();
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());

        JsonElement encoded = Structure.DIRECT_CODEC.encodeStart(ops, structure.value()).result().orElse(null);
        if (encoded != null && encoded.isJsonObject() && encoded.getAsJsonObject().has("start_pool")) {
            tried.add("template pools");
            Registry<StructureTemplatePool> pools = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
            Set<Identifier> visitedPools = new LinkedHashSet<>();
            walkPool(level, pools, ops, Identifier.parse(encoded.getAsJsonObject().get("start_pool").getAsString()), visitedPools, templates);
        }

        Set<ResourceKey<LootTable>> confirmed = new LinkedHashSet<>();
        if (!templates.isEmpty()) {
            tried.add("template block entities (" + templates.size() + " templates)");
            for (Identifier id : templates) {
                Optional<StructureTemplate> template = level.getStructureManager().get(id);
                template.ifPresent(value -> collectLootTables(value, confirmed));
            }
        }

        List<Found> found = new ArrayList<>();
        confirmed.forEach(key -> found.add(new Found(key, true)));
        if (found.isEmpty()) {
            tried.add("loot table ids matching the structure path");
            Identifier key = structure.key().identifier();
            for (ResourceKey<LootTable> tableKey : level.getServer().reloadableRegistries().lookup()
                    .lookupOrThrow(Registries.LOOT_TABLE).listElementIds().toList()) {
                Identifier tableId = tableKey.identifier();
                if (tableId.getNamespace().equals(key.getNamespace()) && tableId.getPath().contains(key.getPath())) {
                    found.add(new Found(tableKey, false));
                }
            }
        }
        return new Result(found, tried);
    }

    private static void walkPool(ServerLevel level, Registry<StructureTemplatePool> pools, DynamicOps<JsonElement> ops, Identifier poolId, Set<Identifier> visited, Set<Identifier> templates) {
        if (!visited.add(poolId)) {
            return;
        }
        StructureTemplatePool pool = pools.getValue(poolId);
        if (pool == null) {
            return;
        }
        JsonElement encoded = StructureTemplatePool.DIRECT_CODEC.encodeStart(ops, pool).result().orElse(null);
        if (encoded == null || !encoded.isJsonObject()) {
            return;
        }
        JsonObject object = encoded.getAsJsonObject();
        if (object.has("fallback")) {
            walkPool(level, pools, ops, Identifier.parse(object.get("fallback").getAsString()), visited, templates);
        }
        if (object.has("elements")) {
            for (JsonElement weighted : object.getAsJsonArray("elements")) {
                if (weighted.isJsonObject() && weighted.getAsJsonObject().has("element")) {
                    collectElement(weighted.getAsJsonObject().get("element"), templates);
                }
            }
        }
        for (Identifier template : new ArrayList<>(templates)) {
            Optional<StructureTemplate> loaded = level.getStructureManager().get(template);
            loaded.ifPresent(value -> collectJigsawPools(value).forEach(next -> walkPool(level, pools, ops, next, visited, templates)));
        }
    }

    private static void collectElement(JsonElement element, Set<Identifier> templates) {
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("location")) {
            templates.add(Identifier.parse(object.get("location").getAsString()));
        }
        if (object.has("elements")) {
            JsonArray nested = object.getAsJsonArray("elements");
            for (JsonElement child : nested) {
                collectElement(child, templates);
            }
        }
    }

    private static List<Identifier> collectJigsawPools(StructureTemplate template) {
        List<Identifier> pools = new ArrayList<>();
        for (CompoundTag nbt : blockEntityTags(template)) {
            nbt.getString("pool").ifPresent(pool -> pools.add(Identifier.parse(pool)));
        }
        return pools;
    }

    private static void collectLootTables(StructureTemplate template, Set<ResourceKey<LootTable>> tables) {
        for (CompoundTag nbt : blockEntityTags(template)) {
            nbt.getString("LootTable").ifPresent(table -> tables.add(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table))));
        }
    }

    private static List<CompoundTag> blockEntityTags(StructureTemplate template) {
        List<CompoundTag> tags = new ArrayList<>();
        CompoundTag saved = template.save(new CompoundTag());
        ListTag blocks = saved.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            blocks.getCompoundOrEmpty(i).getCompound("nbt").ifPresent(tags::add);
        }
        return tags;
    }
}
