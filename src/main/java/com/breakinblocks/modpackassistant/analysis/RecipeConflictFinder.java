package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class RecipeConflictFinder {
    public record Group(int id, RecipeType<?> type, List<RecipeHolder<?>> recipes, List<ItemStack> results, boolean conflict) {
    }

    public static final class Bucket {
        final RecipeType<?> type;
        final int ingredientCount;
        final int width;
        final int height;
        final List<RecipeHolder<?>> recipes = new ArrayList<>();

        Bucket(RecipeType<?> type, int ingredientCount, int width, int height) {
            this.type = type;
            this.ingredientCount = ingredientCount;
            this.width = width;
            this.height = height;
        }

        public int size() {
            return recipes.size();
        }
    }

    private static final class Prepared {
        final RecipeHolder<?> holder;
        final List<Ingredient> ingredients;
        final List<Set<Item>> items;

        Prepared(RecipeHolder<?> holder) {
            this.holder = holder;
            this.ingredients = holder.value().placementInfo().ingredients();
            this.items = new ArrayList<>(ingredients.size());
            for (Ingredient ingredient : ingredients) {
                Set<Item> set = new HashSet<>();
                ingredient.items().forEach(item -> set.add(item.value()));
                items.add(set);
            }
        }
    }

    private final ContextMap displayContext;
    private final List<Bucket> buckets = new ArrayList<>();
    private final List<Identifier> skipped = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private int recipeCount;

    public RecipeConflictFinder(Level level) {
        this.displayContext = SlotDisplayContext.fromLevel(level);
    }

    public List<Bucket> buckets() {
        return buckets;
    }

    public int recipeCount() {
        return recipeCount;
    }

    public List<Identifier> skipped() {
        return skipped;
    }

    public long conflictCount() {
        return groups.stream().filter(Group::conflict).count();
    }

    public long duplicateCount() {
        return groups.stream().filter(group -> !group.conflict()).count();
    }

    public void prepare(Collection<RecipeHolder<?>> recipes, @Nullable RecipeType<?> filter) {
        Map<String, Bucket> byKey = new HashMap<>();
        for (RecipeHolder<?> holder : recipes) {
            Recipe<?> recipe = holder.value();
            if (filter != null && recipe.getType() != filter) {
                continue;
            }
            recipeCount++;
            List<Ingredient> ingredients = recipe.placementInfo().ingredients();
            if (recipe.isSpecial() || ingredients.isEmpty()) {
                skipped.add(holder.id().identifier());
                continue;
            }
            int width = 0;
            int height = 0;
            if (recipe instanceof ShapedRecipe shaped) {
                width = shaped.getWidth();
                height = shaped.getHeight();
            }
            String key = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()) + "|" + ingredients.size() + "|" + width + "x" + height;
            int finalWidth = width;
            int finalHeight = height;
            byKey.computeIfAbsent(key, ignored -> new Bucket(recipe.getType(), ingredients.size(), finalWidth, finalHeight)).recipes.add(holder);
        }
        byKey.values().stream().filter(bucket -> bucket.size() > 1).forEach(buckets::add);
        skipped.sort(Comparator.naturalOrder());
    }

    public void process(Bucket bucket) {
        List<Prepared> prepared = bucket.recipes.stream().map(Prepared::new).toList();
        int[] parent = new int[prepared.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        boolean shaped = bucket.width > 0;
        for (int i = 0; i < prepared.size(); i++) {
            for (int j = i + 1; j < prepared.size(); j++) {
                if (find(parent, i) != find(parent, j) && sameInputs(prepared.get(i), prepared.get(j), shaped)) {
                    parent[find(parent, i)] = find(parent, j);
                }
            }
        }
        Map<Integer, List<RecipeHolder<?>>> byRoot = new HashMap<>();
        for (int i = 0; i < prepared.size(); i++) {
            byRoot.computeIfAbsent(find(parent, i), ignored -> new ArrayList<>()).add(prepared.get(i).holder);
        }
        for (List<RecipeHolder<?>> members : byRoot.values()) {
            if (members.size() < 2) {
                continue;
            }
            members.sort(Comparator.comparing(holder -> holder.id().identifier().toString()));
            List<ItemStack> results = members.stream().map(this::resultOf).toList();
            boolean conflict = false;
            for (int i = 1; i < results.size(); i++) {
                if (!ItemStack.isSameItemSameComponents(results.get(0), results.get(i)) || results.get(0).getCount() != results.get(i).getCount()) {
                    conflict = true;
                    break;
                }
            }
            groups.add(new Group(groups.size() + 1, bucket.type, members, results, conflict));
        }
    }

    private ItemStack resultOf(RecipeHolder<?> holder) {
        for (RecipeDisplay display : holder.value().display()) {
            ItemStack result = display.result().resolveForFirstStack(displayContext);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    private static int find(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    private static boolean sameInputs(Prepared a, Prepared b, boolean shaped) {
        if (a.ingredients.size() != b.ingredients.size()) {
            return false;
        }
        if (shaped) {
            for (int i = 0; i < a.ingredients.size(); i++) {
                if (!overlaps(a, i, b, i)) {
                    return false;
                }
            }
            return true;
        }
        boolean[] used = new boolean[b.ingredients.size()];
        for (int i = 0; i < a.ingredients.size(); i++) {
            boolean matched = false;
            for (int j = 0; j < b.ingredients.size(); j++) {
                if (!used[j] && overlaps(a, i, b, j)) {
                    used[j] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static boolean overlaps(Prepared a, int i, Prepared b, int j) {
        Set<Item> left = a.items.get(i);
        Set<Item> right = b.items.get(j);
        if (left.isEmpty() || right.isEmpty()) {
            return left.isEmpty() == right.isEmpty();
        }
        for (Item item : left) {
            if (right.contains(item)) {
                return true;
            }
        }
        return false;
    }

    public String log(ReportWriter.Context context) {
        List<String> lines = new ArrayList<>(context.commentLines());
        appendSection(lines, "Conflicts", true);
        appendSection(lines, "Duplicates", false);
        lines.add("");
        lines.add("Skipped dynamic recipes (" + skipped.size() + ")");
        lines.add("=".repeat(60));
        skipped.forEach(id -> lines.add(id.toString()));
        lines.add("");
        lines.add("Groups per mod");
        lines.add("=".repeat(60));
        Object2IntOpenHashMap<String> perMod = new Object2IntOpenHashMap<>();
        for (Group group : groups) {
            Set<String> mods = new TreeSet<>();
            group.recipes().forEach(holder -> mods.add(holder.id().identifier().getNamespace()));
            mods.forEach(mod -> perMod.addTo(mod, 1));
        }
        perMod.object2IntEntrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()))
                .forEach(entry -> lines.add(entry.getKey() + ": " + entry.getIntValue()));
        return String.join("\n", lines) + "\n";
    }

    private void appendSection(List<String> lines, String title, boolean conflict) {
        List<Group> selected = groups.stream().filter(group -> group.conflict() == conflict).toList();
        lines.add("");
        lines.add(title + " (" + selected.size() + ")");
        lines.add("=".repeat(60));
        for (Group group : selected) {
            lines.add("Group " + group.id() + " [" + BuiltInRegistries.RECIPE_TYPE.getKey(group.type()) + "]");
            for (int i = 0; i < group.recipes().size(); i++) {
                Identifier id = group.recipes().get(i).id().identifier();
                ItemStack result = group.results().get(i);
                lines.add("    " + id + "  (" + id.getNamespace() + ")  -> " + result.getCount() + "x " + BuiltInRegistries.ITEM.getKey(result.getItem()));
            }
        }
    }

    public String csv(ReportWriter.Context context) {
        CsvWriter csv = new CsvWriter().comments(context.headerLines());
        csv.row("group", "kind", "recipe_type", "recipe", "mod", "result", "result_count");
        for (Group group : groups) {
            for (int i = 0; i < group.recipes().size(); i++) {
                Identifier id = group.recipes().get(i).id().identifier();
                ItemStack result = group.results().get(i);
                csv.row(group.id(), group.conflict() ? "conflict" : "duplicate", BuiltInRegistries.RECIPE_TYPE.getKey(group.type()),
                        id, id.getNamespace(), BuiltInRegistries.ITEM.getKey(result.getItem()), result.getCount());
            }
        }
        return csv.content();
    }
}
