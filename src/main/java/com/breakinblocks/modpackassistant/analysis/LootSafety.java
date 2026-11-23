package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.util.Messages;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
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
            "set_count", "set_item", "set_damage", "set_name", "set_lore", "set_potion", "set_instrument",
            "set_stew_effect", "limit_count", "explosion_decay", "set_components", "set_custom_data",
            "set_custom_model_data", "set_banner_pattern", "set_fireworks", "set_firework_explosion",
            "set_book_cover", "set_written_book_pages", "set_writable_book_pages", "toggle_tooltips",
            "set_ominous_bottle_amplifier", "set_enchantments", "enchant_randomly", "enchant_with_levels",
            "set_attributes", "set_loot_table", "sequence");
    private static final Set<String> CONDITIONS = Set.of(
            "random_chance", "all_of", "any_of", "inverted", "time_check",
            "weather_check", "killed_by_player", "survives_explosion");
    private static final Set<String> TYPES = Set.of(
            "item", "empty", "tag", "loot_table", "alternatives", "sequence", "group",
            "constant", "uniform", "binomial", "generic", "chest", "entity", "block", "fishing",
            "advancement_reward", "advancement_entity", "gift", "barter",
            "command", "selector", "archaeology", "vault", "equipment", "shearing");

    private final ServerLevel level;
    private final ReloadableServerRegistries.Holder registries;
    private final RegistryOps<JsonElement> ops;
    private final ResourceKey<LootTable> root;
    private final LootTable rootTable;
    private final float luck;
    private final ArrayDeque<ResourceKey<?>> pending = new ArrayDeque<>();
    private final Set<ResourceKey<?>> seen = new HashSet<>();
    private final Map<ResourceKey<?>, JsonElement> resources = new HashMap<>();
    private int nodes;
    private boolean complete;
    private long estimatedWork;

    public LootSafety(ServerLevel level, ResourceKey<LootTable> root, float luck) {
        this(level, root, level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).get(root)
                .orElseThrow(() -> new IllegalArgumentException(Messages.UNKNOWN_LOOT_TABLE.get(root.identifier()).getString())).value(), luck);
    }

    public LootSafety(ServerLevel level, ResourceKey<LootTable> root, LootTable table, float luck) {
        this.level = level;
        this.registries = level.getServer().reloadableRegistries();
        this.ops = RegistryOps.create(JsonOps.INSTANCE, registries.lookup());
        this.root = root;
        this.rootTable = table;
        this.luck = luck;
        enqueue(root);
    }

    public boolean step() {
        verifyCurrent();
        if (complete) return true;
        ResourceKey<?> key = pending.poll();
        if (key != null) {
            JsonElement value = encode(key);
            resources.put(key, value);
            inspect(value, 0, slot(key));
        }
        if (!pending.isEmpty()) return false;
        estimatedWork = cost(root, new HashSet<>(), new HashMap<>());
        complete = true;
        return true;
    }

    public void verifyCurrent() {
        if (registries != level.getServer().reloadableRegistries()) {
            throw new IllegalStateException(Messages.LOOT_RELOADED.get().getString());
        }
    }

    public boolean complete() {
        return complete;
    }

    public int rollsPerBatch() {
        return (int) Math.max(1, Math.min(LootSimulator.BATCH, 100_000 / Math.max(1, estimatedWork)));
    }

    private JsonElement encode(ResourceKey<?> key) {
        if (key.isFor(Registries.LOOT_TABLE)) {
            LootTable table = key.equals(root) ? rootTable : get(Registries.LOOT_TABLE, key.identifier());
            return LootTable.DIRECT_CODEC.encodeStart(ops, table).getOrThrow();
        }
        if (key.isFor(Registries.ITEM_MODIFIER)) {
            return LootItemFunctions.DIRECT_CODEC.encodeStart(ops, get(Registries.ITEM_MODIFIER, key.identifier())).getOrThrow();
        }
        return LootItemCondition.DIRECT_CODEC.encodeStart(ops, get(Registries.PREDICATE, key.identifier())).getOrThrow();
    }

    private <T> T get(ResourceKey<? extends Registry<T>> registry, Identifier id) {
        return registries.lookup().lookupOrThrow(registry).get(ResourceKey.create(registry, id))
                .orElseThrow(() -> rejected(id.toString())).value();
    }

    private void enqueue(ResourceKey<?> key) {
        if (seen.add(key)) {
            if (seen.size() > MAX_RESOURCES) throw rejected(Messages.LOOT_RESOURCE_BUDGET.get().getString());
            pending.add(key);
        }
    }

    private void inspect(JsonElement element, int depth, Slot slot) {
        if (++nodes > MAX_NODES || depth > 32) throw rejected(Messages.LOOT_COMPLEXITY_BUDGET.get().getString());
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) inspect(child, depth + 1, slot);
        } else if (element.isJsonPrimitive()) {
            ResourceKey<?> reference = reference(element, slot);
            if (reference != null) enqueue(reference);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            check(object, "type", switch (slot) {
                case FUNCTION -> FUNCTIONS;
                case CONDITION -> CONDITIONS;
                default -> TYPES;
            });
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                inspect(entry.getValue(), depth + 1, child(object, slot, entry.getKey()));
            }
        }
    }

    private void check(JsonObject object, String field, Set<String> allowed) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) return;
        Identifier id = Identifier.tryParse(object.get(field).getAsString());
        if (id == null || !id.getNamespace().equals("minecraft") || !allowed.contains(id.getPath())) {
            throw rejected(field + "=" + object.get(field).getAsString());
        }
    }

    private static Slot slot(ResourceKey<?> key) {
        if (key.isFor(Registries.LOOT_TABLE)) return Slot.TABLE;
        if (key.isFor(Registries.ITEM_MODIFIER)) return Slot.FUNCTION;
        return Slot.CONDITION;
    }

    private static Slot child(JsonObject parent, Slot slot, String field) {
        return switch (field) {
            case "modifier" -> Slot.FUNCTION;
            case "functions" -> slot == Slot.FUNCTION ? Slot.FUNCTION : Slot.NODE;
            case "condition", "term", "terms" -> Slot.CONDITION;
            case "value" -> slot == Slot.NODE && is(parent, "type", "loot_table") ? Slot.TABLE : Slot.NODE;
            default -> Slot.NODE;
        };
    }

    private ResourceKey<?> reference(JsonElement element, Slot slot) {
        if (slot == Slot.NODE || !element.getAsJsonPrimitive().isString()) return null;
        String value = element.getAsString();
        Identifier id = value.startsWith("#") ? null : Identifier.tryParse(value);
        if (id == null) throw rejected(value);
        return switch (slot) {
            case TABLE -> ResourceKey.create(Registries.LOOT_TABLE, id);
            case FUNCTION -> ResourceKey.create(Registries.ITEM_MODIFIER, id);
            default -> ResourceKey.create(Registries.PREDICATE, id);
        };
    }

    private static boolean is(JsonObject object, String field, String value) {
        return object.has(field) && object.get(field).isJsonPrimitive()
                && Identifier.withDefaultNamespace(value).equals(Identifier.tryParse(object.get(field).getAsString()));
    }

    private static Identifier id(JsonObject object, String field) {
        return Identifier.parse(object.get(field).getAsString());
    }

    private long cost(ResourceKey<?> key, Set<ResourceKey<?>> active, Map<ResourceKey<?>, Long> costs) {
        if (costs.containsKey(key)) return costs.get(key);
        if (!active.add(key)) throw rejected(Messages.LOOT_RECURSIVE.get().getString());
        long result = cost(resources.get(key), slot(key), active, costs);
        active.remove(key);
        costs.put(key, result);
        return result;
    }

    private long cost(JsonElement element, Slot slot, Set<ResourceKey<?>> active, Map<ResourceKey<?>, Long> costs) {
        long value = 1;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) value = bounded(value + cost(child, slot, active, costs));
        } else if (element.isJsonPrimitive()) {
            ResourceKey<?> reference = reference(element, slot);
            if (reference != null) value = bounded(value + cost(reference, active, costs));
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                value = bounded(value + cost(entry.getValue(), child(object, slot, entry.getKey()), active, costs));
            }
            if (slot == Slot.NODE && is(object, "type", "tag") && object.has("items")) {
                value = bounded(value + tagSize(object.get("items")));
            }
            if (object.has("modifier")) value = bounded(value * cost(object.get("modifier"), Slot.FUNCTION, active, costs));
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

    private int tagSize(JsonElement items) {
        if (items.isJsonArray()) return items.getAsJsonArray().size();
        if (!items.isJsonPrimitive() || !items.getAsString().startsWith("#")) return 1;
        Identifier id = Identifier.tryParse(items.getAsString().substring(1));
        if (id == null) throw rejected(items.getAsString());
        var tag = net.minecraft.tags.TagKey.create(Registries.ITEM, id);
        return registries.lookup().lookupOrThrow(Registries.ITEM).get(tag).map(net.minecraft.core.HolderSet::size).orElse(0);
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

    private enum Slot {
        TABLE,
        FUNCTION,
        CONDITION,
        NODE
    }

    private IllegalArgumentException rejected(String reason) {
        return new IllegalArgumentException(Messages.LOOT_UNSAFE.get(root.identifier(), reason).getString());
    }
}
