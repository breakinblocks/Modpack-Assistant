package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collections;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ObtainabilityIndex {
    private final ServerLevel level;
    private final Set<Item> fromRecipes = new HashSet<>();
    private final Set<Item> fromLoot = new HashSet<>();
    private final Set<Item> fromTrades = new HashSet<>();
    private final Set<Item> fromCreative = new HashSet<>();
    private final List<String> checked = new ArrayList<>();
    private final List<String> unchecked = new ArrayList<>();
    private final List<ResourceKey<LootTable>> lootTableIds;
    private final Map<String, List<Item>> uncraftable = new TreeMap<>();
    private final Map<String, List<Item>> creativeOnly = new TreeMap<>();
    private int uncraftableCount;
    private int creativeOnlyCount;
    private final Iterator<RecipeHolder<?>> recipes;
    private final Iterator<VillagerTrade> trades;
    private final Iterator<Holder.Reference<Item>> items = BuiltInRegistries.ITEM.listElements().iterator();
    private final ArrayDeque<Iterator<JsonElement>> lootNodes = new ArrayDeque<>();
    private Iterator<ItemStack> creativeItems = Collections.emptyIterator();
    private Iterator<CreativeModeTab> creativeTabs;
    private int phase;
    private int lootIndex;
    private int recipesRead;
    private int recipesSkipped;
    private int tradesRead;
    private int tradesSkipped;
    private int lootSkipped;

    public ObtainabilityIndex(ServerLevel level) {
        this.level = level;
        this.lootTableIds = level.getServer().reloadableRegistries().lookup()
                .lookupOrThrow(Registries.LOOT_TABLE).listElementIds().toList();
        recipes = level.getServer().getRecipeManager().getRecipes().iterator();
        trades = level.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).iterator();
    }

    public int lootTableCount() {
        return lootTableIds.size();
    }

    public int uncraftableCount() {
        return uncraftableCount;
    }

    public int creativeOnlyCount() {
        return creativeOnlyCount;
    }

    public int modCount() {
        return uncraftable.size();
    }

    public boolean step(@Nullable String namespace, int budget) {
        if (budget < 1) throw new IllegalArgumentException("budget must be positive");
        ContextMap displayContext = SlotDisplayContext.fromLevel(level);
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.getServer().reloadableRegistries().lookup());
        for (int i = 0; i < budget; i++) {
            switch (phase) {
                case 0 -> {
                    if (!recipes.hasNext()) {
                        checked.add("static recipe displays (" + recipesRead + " recipes)");
                        if (recipesSkipped > 0) unchecked.add("recipes without readable static results (" + recipesSkipped + ")");
                        phase++;
                        continue;
                    }
                    RecipeHolder<?> holder = recipes.next();
                    recipesRead++;
                    boolean found = false;
                    try {
                        for (RecipeDisplay display : holder.value().display()) {
                            for (ItemStack result : display.result().resolveForStacks(displayContext)) {
                                if (!result.isEmpty()) {
                                    fromRecipes.add(result.getItem());
                                    found = true;
                                }
                            }
                        }
                    } catch (RuntimeException error) {
                        ModpackAssistant.LOGGER.debug("Recipe {} has no readable static result", holder.id().identifier(), error);
                    }
                    if (!found) recipesSkipped++;
                }
                case 1 -> {
                    if (!lootNodes.isEmpty()) {
                        Iterator<JsonElement> nodes = lootNodes.peek();
                        if (!nodes.hasNext()) lootNodes.pop();
                        else visitLootNode(nodes.next());
                    } else if (lootIndex < lootTableIds.size()) {
                        var id = lootTableIds.get(lootIndex++);
                        try {
                            LootTable table = level.getServer().reloadableRegistries().getLootTable(id);
                            JsonElement encoded = LootTable.DIRECT_CODEC.encodeStart(ops, table).result().orElse(null);
                            if (encoded == null) lootSkipped++;
                            else lootNodes.push(List.of(encoded).iterator());
                        } catch (RuntimeException error) {
                            lootSkipped++;
                            ModpackAssistant.LOGGER.debug("Could not inspect loot table {}", id.identifier(), error);
                        }
                        return false;
                    } else {
                        checked.add("loot tables (" + lootTableIds.size() + " tables, item and tag entries)");
                        if (lootSkipped > 0) unchecked.add("unreadable loot tables (" + lootSkipped + ")");
                        phase++;
                    }
                }
                case 2 -> {
                    if (!trades.hasNext()) {
                        checked.add("registered villager trades (" + tradesRead + " listings; availability conditions not evaluated)");
                        if (tradesSkipped > 0) unchecked.add("unreadable villager trade outputs (" + tradesSkipped + ")");
                        phase++;
                        continue;
                    }
                    tradesRead++;
                    try {
                        JsonElement encoded = VillagerTrade.CODEC.encodeStart(ops, trades.next()).result().orElse(null);
                        if (encoded == null || !encoded.isJsonObject() || !collectGiven(encoded.getAsJsonObject().get("gives"))) tradesSkipped++;
                    } catch (RuntimeException error) {
                        tradesSkipped++;
                        ModpackAssistant.LOGGER.debug("Could not inspect villager trade output", error);
                    }
                }
                case 3 -> {
                    if (creativeTabs == null) {
                        try {
                            CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
                            creativeTabs = CreativeModeTabs.allTabs().iterator();
                        } catch (RuntimeException error) {
                            unchecked.add("creative tab contents (" + error.getMessage() + ")");
                            creativeTabs = Collections.emptyIterator();
                        }
                        return false;
                    }
                    if (creativeItems.hasNext()) fromCreative.add(creativeItems.next().getItem());
                    else if (creativeTabs.hasNext()) creativeItems = creativeTabs.next().getDisplayItems().iterator();
                    else {
                        checked.add("available creative tab contents");
                        phase++;
                    }
                }
                case 4 -> {
                    if (!items.hasNext()) {
                        phase++;
                        return true;
                    }
                    Holder.Reference<Item> holder = items.next();
                    Item item = holder.value();
                    Identifier id = holder.key().identifier();
                    if (item == Items.AIR || (namespace != null && !id.getNamespace().equals(namespace))
                            || fromRecipes.contains(item) || fromLoot.contains(item) || fromTrades.contains(item)) continue;
                    if (fromCreative.contains(item)) {
                        creativeOnly.computeIfAbsent(id.getNamespace(), ignored -> new ArrayList<>()).add(item);
                        creativeOnlyCount++;
                    } else {
                        uncraftable.computeIfAbsent(id.getNamespace(), ignored -> new ArrayList<>()).add(item);
                        uncraftableCount++;
                    }
                }
                default -> { return true; }
            }
        }
        return false;
    }

    private void visitLootNode(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement type = object.get("type");
            JsonElement name = object.get("name");
            if (type != null && type.isJsonPrimitive() && name != null && name.isJsonPrimitive()) {
                Identifier id = Identifier.tryParse(name.getAsString());
                if (id != null) {
                    switch (type.getAsString()) {
                        case "minecraft:item", "item" -> BuiltInRegistries.ITEM.getOptional(id).ifPresent(fromLoot::add);
                        case "minecraft:tag", "tag" -> BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id))
                                .ifPresent(set -> set.forEach(holder -> fromLoot.add(holder.value())));
                    }
                }
            }
            lootNodes.push(object.asMap().values().iterator());
        } else if (element.isJsonArray()) {
            lootNodes.push(element.getAsJsonArray().iterator());
        }
    }

    private boolean collectGiven(@Nullable JsonElement gives) {
        if (gives == null) return false;
        JsonElement idElement = gives.isJsonObject() ? gives.getAsJsonObject().get("id") : gives;
        if (idElement == null || !idElement.isJsonPrimitive()) return false;
        Identifier id = Identifier.tryParse(idElement.getAsString());
        if (id == null) return false;
        return BuiltInRegistries.ITEM.getOptional(id).map(fromTrades::add).isPresent();
    }

    public String log(ReportWriter.Context context) {
        List<String> lines = new ArrayList<>(context.commentLines());
        lines.add("# sources checked: " + String.join("; ", checked));
        lines.add("# sources not checked: " + (unchecked.isEmpty() ? "none" : String.join("; ", unchecked)));
        lines.add("# This is a candidate list. Quests, scripts and mod mechanics can grant items that no registry describes.");
        lines.add("");
        lines.add("Per mod");
        lines.add("=".repeat(60));
        uncraftable.forEach((mod, items) -> lines.add(mod + ": " + items.size()));
        lines.add("");
        lines.add("Items with no known source (" + uncraftableCount + ")");
        lines.add("=".repeat(60));
        appendItems(lines, uncraftable);
        lines.add("");
        lines.add("Creative tab only (" + creativeOnlyCount + ")");
        lines.add("=".repeat(60));
        appendItems(lines, creativeOnly);
        return String.join("\n", lines) + "\n";
    }

    private static void appendItems(List<String> lines, Map<String, List<Item>> byMod) {
        byMod.forEach((mod, items) -> {
            lines.add("[" + mod + "]");
            items.stream().map(BuiltInRegistries.ITEM::getKey).map(Identifier::toString).sorted().forEach(id -> lines.add("    " + id));
        });
    }

    public String csv(ReportWriter.Context context) {
        CsvWriter csv = new CsvWriter().comments(context.headerLines())
                .comment("sources checked: " + String.join("; ", checked))
                .comment("sources not checked: " + (unchecked.isEmpty() ? "none" : String.join("; ", unchecked)));
        csv.row("item", "mod", "creative_only");
        appendRows(csv, uncraftable, false);
        appendRows(csv, creativeOnly, true);
        return csv.content();
    }

    private static void appendRows(CsvWriter csv, Map<String, List<Item>> byMod, boolean creativeOnly) {
        byMod.forEach((mod, items) -> items.stream().map(BuiltInRegistries.ITEM::getKey).sorted().forEach(id -> csv.row(id, mod, creativeOnly)));
    }
}
