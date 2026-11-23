package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.util.Messages;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LootSafety {
    private static final int MAX_NODES = 4096;
    private static final int MAX_RESOURCES = 64;
    private static final int MAX_WORK = 10_000;
    private static final Set<String> FUNCTIONS = Set.of(
            "set_count", "set_damage", "set_name", "set_lore", "set_potion", "set_instrument",
            "set_stew_effect", "limit_count", "explosion_decay", "set_nbt", "set_banner_pattern",
            "set_enchantments", "enchant_randomly", "enchant_with_levels", "set_attributes",
            "set_loot_table", "reference");
    private static final Set<String> CONDITIONS = Set.of(
            "random_chance", "all_of", "any_of", "inverted", "reference", "time_check",
            "weather_check", "killed_by_player", "survives_explosion");
    private static final Set<String> TYPES = Set.of(
            "item", "empty", "tag", "loot_table", "alternatives", "sequence", "group",
            "constant", "uniform", "binomial", "generic", "chest", "entity", "block", "fishing",
            "advancement_reward", "advancement_entity", "advancement_location", "gift", "barter",
            "command", "selector", "archaeology");

    private final ServerLevel level;
    private final LootDataManager lootData;
    private final ResourceLocation root;
    private final LootTable rootTable;
    private final float luck;
    private final ArrayDeque<LootDataId<?>> pending = new ArrayDeque<>();
    private final Set<LootDataId<?>> seen = new HashSet<>();
    private final Map<LootDataId<?>, JsonElement> resources = new HashMap<>();
    private int nodes;
    private boolean complete;
    private long estimatedWork;

    public LootSafety(ServerLevel level, ResourceLocation root, float luck) {
        this(level, root, table(level, root), luck);
    }

    public LootSafety(ServerLevel level, ResourceLocation root, LootTable table, float luck) {
        this.level = level;
        this.lootData = level.getServer().getLootData();
        this.root = root;
        this.rootTable = table;
        this.luck = luck;
        enqueue(new LootDataId<>(LootDataType.TABLE, root));
    }

    private static LootTable table(ServerLevel level, ResourceLocation root) {
        LootTable table = level.getServer().getLootData().getElement(new LootDataId<>(LootDataType.TABLE, root));
        if (table == null) {
            throw new IllegalArgumentException(Messages.UNKNOWN_LOOT_TABLE.get(root).getString());
        }
        return table;
    }

    public boolean step() {
        verifyCurrent();
        if (complete) return true;
        LootDataId<?> key = pending.poll();
        if (key != null) {
            JsonElement value = encode(key);
            resources.put(key, value);
            inspect(value, 0);
        }
        if (!pending.isEmpty()) return false;
        estimatedWork = cost(new LootDataId<>(LootDataType.TABLE, root), new HashSet<>(), new HashMap<>());
        complete = true;
        return true;
    }

    public void verifyCurrent() {
        if (lootData != level.getServer().getLootData()) {
            throw new IllegalStateException(Messages.LOOT_RELOADED.get().getString());
        }
    }

    public boolean complete() {
        return complete;
    }

    public int rollsPerBatch() {
        return (int) Math.max(1, Math.min(LootSimulator.BATCH, 100_000 / Math.max(1, estimatedWork)));
    }

    private JsonElement encode(LootDataId<?> key) {
        Object value = key.type() == LootDataType.TABLE && key.location().equals(root) ? rootTable : lootData.getElement(key);
        if (value == null) throw rejected(key.location().toString());
        Class<?> type = key.type() == LootDataType.TABLE ? LootTable.class
                : key.type() == LootDataType.MODIFIER ? LootItemFunction.class : LootItemCondition.class;
        return key.type().parser().toJsonTree(value, type);
    }

    private void enqueue(LootDataId<?> key) {
        if (seen.add(key)) {
            if (seen.size() > MAX_RESOURCES) throw rejected(Messages.LOOT_RESOURCE_BUDGET.get().getString());
            pending.add(key);
        }
    }

    private void inspect(JsonElement element, int depth) {
        if (++nodes > MAX_NODES || depth > 32) throw rejected(Messages.LOOT_COMPLEXITY_BUDGET.get().getString());
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) inspect(child, depth + 1);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            check(object, "function", FUNCTIONS);
            check(object, "condition", CONDITIONS);
            check(object, "type", TYPES);
            LootDataId<?> reference = reference(object);
            if (reference != null) enqueue(reference);
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                inspect(entry.getValue(), depth + 1);
            }
        }
    }

    private void check(JsonObject object, String field, Set<String> allowed) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) return;
        ResourceLocation id = ResourceLocation.tryParse(object.get(field).getAsString());
        if (id == null || !id.getNamespace().equals("minecraft") || !allowed.contains(id.getPath())) {
            throw rejected(field + "=" + object.get(field).getAsString());
        }
    }

    private LootDataId<?> reference(JsonObject object) {
        if (is(object, "function", "reference")) return new LootDataId<>(LootDataType.MODIFIER, id(object, "name"));
        if (is(object, "condition", "reference")) return new LootDataId<>(LootDataType.PREDICATE, id(object, "name"));
        if (is(object, "type", "loot_table") && object.has("name") && object.get("name").isJsonPrimitive()) {
            return new LootDataId<>(LootDataType.TABLE, id(object, "name"));
        }
        return null;
    }

    private static boolean is(JsonObject object, String field, String value) {
        return object.has(field) && object.get(field).isJsonPrimitive()
                && new ResourceLocation(value).equals(ResourceLocation.tryParse(object.get(field).getAsString()));
    }

    private ResourceLocation id(JsonObject object, String field) {
        ResourceLocation id = ResourceLocation.tryParse(object.get(field).getAsString());
        if (id == null) throw rejected(object.get(field).getAsString());
        return id;
    }

    private long cost(LootDataId<?> key, Set<LootDataId<?>> active, Map<LootDataId<?>, Long> costs) {
        if (costs.containsKey(key)) return costs.get(key);
        if (!active.add(key)) throw rejected(Messages.LOOT_RECURSIVE.get().getString());
        long result = cost(resources.get(key), active, costs);
        active.remove(key);
        costs.put(key, result);
        return result;
    }

    private long cost(JsonElement element, Set<LootDataId<?>> active, Map<LootDataId<?>, Long> costs) {
        long value = 1;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) value = bounded(value + cost(child, active, costs));
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> child : object.entrySet()) value = bounded(value + cost(child.getValue(), active, costs));
            LootDataId<?> reference = reference(object);
            if (reference != null) value = bounded(value + cost(reference, active, costs));
            if (is(object, "type", "tag")) {
                var tag = net.minecraft.tags.TagKey.create(Registries.ITEM, id(object, "name"));
                int size = BuiltInRegistries.ITEM.getTag(tag).map(set -> set.size()).orElse(0);
                value = bounded(value + size);
            }
            if (object.has("functions")) value = bounded(value * cost(object.get("functions"), active, costs));
            if (object.has("rolls")) {
                double rolls = upper(object.get("rolls"));
                if (object.has("bonus_rolls")) rolls += Math.max(0, luck) * upper(object.get("bonus_rolls"));
                if (!Double.isFinite(rolls) || rolls > MAX_WORK) throw rejected(Messages.LOOT_ROLL_BUDGET.get().getString());
                value = bounded(value * Math.max(1, (long) Math.ceil(rolls)));
            }
            if (is(object, "type", "binomial")) {
                value = bounded(value * Math.max(1, (long) Math.ceil(upper(object.get("n")))));
            }
        }
        return value;
    }

    private double upper(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsDouble();
        if (!value.isJsonObject()) throw rejected(Messages.LOOT_UNBOUNDED_PROVIDER.get().getString());
        JsonObject object = value.getAsJsonObject();
        if (is(object, "type", "constant")) return upper(object.get("value"));
        if (is(object, "type", "uniform")) return Math.max(upper(object.get("min")), upper(object.get("max")));
        if (is(object, "type", "binomial")) return upper(object.get("n"));
        throw rejected(Messages.LOOT_UNBOUNDED_PROVIDER.get().getString());
    }

    private long bounded(long value) {
        if (value < 0 || value > MAX_WORK) throw rejected(Messages.LOOT_ROLL_BUDGET.get().getString());
        return value;
    }

    private IllegalArgumentException rejected(String reason) {
        return new IllegalArgumentException(Messages.LOOT_UNSAFE.get(root, reason).getString());
    }
}
