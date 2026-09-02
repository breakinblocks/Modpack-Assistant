package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ObtainabilityIndex {
    private static final int TRADE_SAMPLES = 8;

    private final ServerLevel level;
    private final Set<Item> fromRecipes = new HashSet<>();
    private final Set<Item> fromLoot = new HashSet<>();
    private final Set<Item> fromTrades = new HashSet<>();
    private final Set<Item> fromCreative = new HashSet<>();
    private final List<String> checked = new ArrayList<>();
    private final List<String> unchecked = new ArrayList<>();
    private final List<ResourceLocation> lootTableIds;
    private final Map<String, List<Item>> uncraftable = new TreeMap<>();
    private final Map<String, List<Item>> creativeOnly = new TreeMap<>();
    private int uncraftableCount;
    private int creativeOnlyCount;

    public ObtainabilityIndex(ServerLevel level) {
        this.level = level;
        this.lootTableIds = new ArrayList<>(level.getServer().reloadableRegistries().getKeys(Registries.LOOT_TABLE));
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

    public void indexRecipes() {
        for (RecipeHolder<?> holder : level.getServer().getRecipeManager().getRecipes()) {
            try {
                ItemStack result = holder.value().getResultItem(level.registryAccess());
                if (!result.isEmpty()) {
                    fromRecipes.add(result.getItem());
                }
            } catch (Exception e) {
                ModpackAssistant.LOGGER.debug("Recipe {} has no static result", holder.id());
            }
        }
        checked.add("recipe results (" + level.getServer().getRecipeManager().getRecipes().size() + " recipes)");
    }

    public void indexLootTables(int from, int to) {
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        for (int i = from; i < Math.min(to, lootTableIds.size()); i++) {
            ResourceLocation id = lootTableIds.get(i);
            LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
            LootTable.DIRECT_CODEC.encodeStart(ops, table).result().ifPresent(this::walkLootJson);
        }
        if (to >= lootTableIds.size()) {
            checked.add("loot tables (" + lootTableIds.size() + " tables, item and tag entries)");
        }
    }

    private void walkLootJson(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") && object.has("name")) {
                String type = object.get("type").getAsString();
                String name = object.get("name").getAsString();
                if (type.equals("minecraft:item") || type.equals("item")) {
                    BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(name)).ifPresent(fromLoot::add);
                } else if (type.equals("minecraft:tag") || type.equals("tag")) {
                    TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(name));
                    BuiltInRegistries.ITEM.getTag(tag).ifPresent(set -> set.forEach(holder -> fromLoot.add(holder.value())));
                }
            }
            object.entrySet().forEach(entry -> walkLootJson(entry.getValue()));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(this::walkLootJson);
        }
    }

    public void indexTrades() {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            unchecked.add("villager trades (could not create a throwaway villager)");
            return;
        }
        RandomSource random = RandomSource.create(0L);
        int listings = 0;
        int skippedMaps = 0;
        for (Map.Entry<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> profession : VillagerTrades.TRADES.entrySet()) {
            for (VillagerTrades.ItemListing[] tier : profession.getValue().values()) {
                for (VillagerTrades.ItemListing listing : tier) {
                    if (isMapListing(listing)) {
                        skippedMaps++;
                        continue;
                    }
                    listings++;
                    sampleListing(listing, villager, random);
                }
            }
        }
        for (VillagerTrades.ItemListing[] tier : VillagerTrades.WANDERING_TRADER_TRADES.values()) {
            for (VillagerTrades.ItemListing listing : tier) {
                if (isMapListing(listing)) {
                    skippedMaps++;
                    continue;
                }
                listings++;
                sampleListing(listing, villager, random);
            }
        }
        villager.discard();
        checked.add("villager and wandering trader trades (" + listings + " listings sampled " + TRADE_SAMPLES + " times each)");
        if (skippedMaps > 0) {
            unchecked.add("treasure map trades (" + skippedMaps + " listings, skipped because they search for structures)");
        }
    }

    private static boolean isMapListing(VillagerTrades.ItemListing listing) {
        return listing.getClass().getSimpleName().contains("Map");
    }

    private void sampleListing(VillagerTrades.ItemListing listing, Villager villager, RandomSource random) {
        for (int i = 0; i < TRADE_SAMPLES; i++) {
            try {
                MerchantOffer offer = listing.getOffer(villager, random);
                if (offer != null && !offer.getResult().isEmpty()) {
                    fromTrades.add(offer.getResult().getItem());
                }
            } catch (Exception e) {
                ModpackAssistant.LOGGER.debug("Trade listing {} could not be sampled", listing.getClass().getName(), e);
            }
        }
    }

    public void indexCreativeTabs() {
        try {
            CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                for (ItemStack stack : tab.getDisplayItems()) {
                    fromCreative.add(stack.getItem());
                }
            }
            checked.add("creative tab contents (" + CreativeModeTabs.allTabs().size() + " tabs)");
        } catch (Exception e) {
            ModpackAssistant.LOGGER.warn("Creative tab contents could not be built on this server", e);
            unchecked.add("creative tab contents (" + e.getMessage() + ")");
        }
    }

    public void finish(@Nullable String namespace) {
        for (Holder.Reference<Item> holder : BuiltInRegistries.ITEM.holders().toList()) {
            Item item = holder.value();
            ResourceLocation id = holder.key().location();
            if (item == Items.AIR || (namespace != null && !id.getNamespace().equals(namespace))) {
                continue;
            }
            if (fromRecipes.contains(item) || fromLoot.contains(item) || fromTrades.contains(item)) {
                continue;
            }
            if (fromCreative.contains(item)) {
                creativeOnly.computeIfAbsent(id.getNamespace(), ignored -> new ArrayList<>()).add(item);
                creativeOnlyCount++;
            } else {
                uncraftable.computeIfAbsent(id.getNamespace(), ignored -> new ArrayList<>()).add(item);
                uncraftableCount++;
            }
        }
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
            items.stream().map(BuiltInRegistries.ITEM::getKey).map(ResourceLocation::toString).sorted().forEach(id -> lines.add("    " + id));
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
