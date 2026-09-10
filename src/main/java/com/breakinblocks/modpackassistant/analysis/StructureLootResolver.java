package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.util.Messages;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class StructureLootResolver {
    public record Found(ResourceKey<LootTable> table, boolean confirmed) {}
    public record Result(List<Found> tables, List<String> tried) {
        public boolean isEmpty() { return tables.isEmpty(); }
    }

    private final ServerLevel level;
    private final Holder.Reference<Structure> structure;
    private final DynamicOps<JsonElement> ops;
    private final ArrayDeque<Identifier> pools = new ArrayDeque<>();
    private final ArrayDeque<Identifier> templates = new ArrayDeque<>();
    private final ArrayDeque<JsonElement> json = new ArrayDeque<>();
    private final Set<Identifier> visitedPools = new HashSet<>();
    private final Set<Identifier> visitedTemplates = new HashSet<>();
    private final Set<Identifier> visitedTables = new HashSet<>();
    private final List<Found> found = new ArrayList<>();
    private final List<String> tried = new ArrayList<>();
    private final int resourceLimit = Math.min(4096, MAConfig.maxStructureLootChests() * 16);
    private Iterator<Identifier> heuristic;
    private ListTag blocks;
    private int blockIndex;
    private int nodes;
    private boolean initialized;

    public StructureLootResolver(ServerLevel level, Holder.Reference<Structure> structure) {
        this.level = level;
        this.structure = structure;
        ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    }

    public boolean step() {
        if (!initialized) {
            initialized = true;
            JsonElement encoded = Structure.DIRECT_CODEC.encodeStart(ops, structure.value()).getOrThrow();
            if (encoded.isJsonObject() && encoded.getAsJsonObject().has("start_pool")) {
                JsonElement pool = encoded.getAsJsonObject().get("start_pool");
                tried.add("template pools and template block entities");
                if (pool.isJsonPrimitive()) addPool(Identifier.parse(pool.getAsString()));
                else json.add(pool);
            }
            return false;
        }
        for (int i = 0; i < 128; i++) {
            if (!json.isEmpty()) {
                inspect(json.remove());
            } else if (blocks != null && blockIndex < blocks.size()) {
                CompoundTag nbt = blocks.getCompoundOrEmpty(blockIndex++).getCompoundOrEmpty("nbt");
                nbt.getString("pool").ifPresent(value -> addPool(Identifier.parse(value)));
                nbt.getString("LootTable").ifPresent(value -> addTable(Identifier.parse(value), true));
            } else {
                blocks = null;
                if (!pools.isEmpty()) {
                    StructureTemplatePool pool = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getValue(pools.remove());
                    if (pool != null) json.add(StructureTemplatePool.DIRECT_CODEC.encodeStart(ops, pool).getOrThrow());
                    return false;
                }
                if (!templates.isEmpty()) {
                    var template = level.getStructureManager().get(templates.remove());
                    if (template.isPresent()) {
                        var size = template.get().getSize();
                        if ((long) size.getX() * size.getY() * size.getZ() > MAConfig.maxDrainBlocks()) throw tooComplex();
                        blocks = template.get().save(new CompoundTag()).getListOrEmpty("blocks");
                        blockIndex = 0;
                    }
                    return false;
                }
                if (!found.isEmpty() && heuristic == null) return true;
                if (heuristic == null) {
                    tried.add("loot table ids matching the structure path");
                    heuristic = level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElementIds().map(ResourceKey::identifier).iterator();
                }
                if (!heuristic.hasNext()) return true;
                Identifier table = heuristic.next();
                Identifier key = structure.key().identifier();
                if (table.getNamespace().equals(key.getNamespace()) && table.getPath().contains(key.getPath())) addTable(table, false);
            }
        }
        return false;
    }

    private void inspect(JsonElement element) {
        if (++nodes > resourceLimit * 256) throw tooComplex();
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(json::add);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("fallback") && object.get("fallback").isJsonPrimitive()) addPool(Identifier.parse(object.get("fallback").getAsString()));
            if (object.has("location") && object.get("location").isJsonPrimitive()) {
                Identifier template = Identifier.parse(object.get("location").getAsString());
                if (visitedTemplates.add(template)) {
                    if (visitedTemplates.size() > resourceLimit) throw tooComplex();
                    templates.add(template);
                }
            }
            for (var entry : object.entrySet()) {
                if (entry.getValue().isJsonObject() || entry.getValue().isJsonArray()) json.add(entry.getValue());
            }
        }
        if (json.size() > resourceLimit * 256) throw tooComplex();
    }

    private void addPool(Identifier pool) {
        if (visitedPools.add(pool)) {
            if (visitedPools.size() > resourceLimit) throw tooComplex();
            pools.add(pool);
        }
    }

    private void addTable(Identifier table, boolean confirmed) {
        if (visitedTables.add(table)) {
            if (found.size() >= MAConfig.maxStructureLootChests()) throw tooComplex();
            found.add(new Found(ResourceKey.create(Registries.LOOT_TABLE, table), confirmed));
        }
    }

    public Result result() {
        return new Result(List.copyOf(found), List.copyOf(tried));
    }

    private IllegalArgumentException tooComplex() {
        return new IllegalArgumentException(Messages.STRUCTLOOT_COMPLEX.get(resourceLimit, MAConfig.maxDrainBlocks(),
                MAConfig.maxStructureLootChests()).getString());
    }
}
