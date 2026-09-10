package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;

public final class RecipeConflictFinder {
    public record Group(int id, RecipeType<?> type, List<RecipeHolder<?>> recipes, List<ItemStack> results, boolean conflict) {
    }

    public static final class Bucket {
        final RecipeType<?> type;
        final List<RecipeHolder<?>> recipes = new ArrayList<>();
        private Work work;

        Bucket(RecipeType<?> type) {
            this.type = type;
        }

        public int size() {
            return recipes.size();
        }
    }

    private static final class Prepared {
        final RecipeHolder<?> holder;
        final List<Ingredient> ingredients;
        final List<Integer> occupied = new ArrayList<>();

        Prepared(RecipeHolder<?> holder) {
            this.holder = holder;
            ingredients = holder.value().getIngredients();
            for (int i = 0; i < ingredients.size(); i++) {
                if (!ingredients.get(i).isEmpty()) occupied.add(i);
            }
        }
    }

    private static final class Members {
        final List<RecipeHolder<?>> recipes = new ArrayList<>();
        final List<ItemStack> results = new ArrayList<>();
        boolean conflict;

        void add(Prepared prepared, HolderLookup.Provider lookup) {
            ItemStack result = prepared.holder.value().getResultItem(lookup);
            if (!results.isEmpty()) {
                ItemStack first = results.getFirst();
                conflict |= !ItemStack.isSameItemSameComponents(first, result) || first.getCount() != result.getCount();
            }
            recipes.add(prepared.holder);
            results.add(result);
        }
    }

    private static final class Work {
        final List<Prepared> prepared = new ArrayList<>();
        final int[] parent;
        final Map<Integer, Members> members = new LinkedHashMap<>();
        int left;
        int right = 1;
        int grouped;
        Iterator<Members> output;

        Work(int size) {
            parent = new int[size];
        }
    }

    private final HolderLookup.Provider lookup;
    private final List<Bucket> buckets = new ArrayList<>();
    private final Map<String, Bucket> byKey = new HashMap<>();
    private final List<ResourceLocation> skipped = new ArrayList<>();
    private final List<Group> groups = new ArrayList<>();
    private int recipeCount;

    public RecipeConflictFinder(HolderLookup.Provider lookup) {
        this.lookup = lookup;
    }

    public List<Bucket> buckets() {
        return buckets;
    }

    public int recipeCount() {
        return recipeCount;
    }

    public List<ResourceLocation> skipped() {
        return skipped;
    }

    public long conflictCount() {
        return groups.stream().filter(Group::conflict).count();
    }

    public long duplicateCount() {
        return groups.stream().filter(group -> !group.conflict()).count();
    }

    public void prepare(Collection<RecipeHolder<?>> recipes, @Nullable RecipeType<?> filter) {
        recipes.forEach(holder -> addRecipe(holder, filter));
    }

    public void addRecipe(RecipeHolder<?> holder, @Nullable RecipeType<?> filter) {
        Recipe<?> recipe = holder.value();
        if (filter != null && recipe.getType() != filter) return;
        recipeCount++;
        if (recipe.isSpecial() || recipe.getIngredients().isEmpty()
                || recipe.getIngredients().size() > 81
                || recipe.getIngredients().stream().anyMatch(ingredient -> !ingredient.isSimple())) {
            skipped.add(holder.id());
            return;
        }
        long count = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).count();
        String key = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()) + "|" + count;
        Bucket bucket = byKey.computeIfAbsent(key, ignored -> new Bucket(recipe.getType()));
        bucket.recipes.add(holder);
        if (bucket.size() == 2) buckets.add(bucket);
    }

    public void process(Bucket bucket) {
        while (!processBatch(bucket, 256)) {}
    }

    public boolean processBatch(Bucket bucket, int budget) {
        if (budget < 1) throw new IllegalArgumentException("budget must be positive");
        if (bucket.work == null) bucket.work = new Work(bucket.size());
        Work work = bucket.work;
        for (int operation = 0; operation < budget; operation++) {
            if (work.prepared.size() < bucket.size()) {
                int index = work.prepared.size();
                work.parent[index] = index;
                work.prepared.add(new Prepared(bucket.recipes.get(index)));
            } else if (work.left < work.prepared.size() - 1) {
                int a = work.left;
                int b = work.right;
                if (find(work.parent, a) != find(work.parent, b)
                        && sameInputs(work.prepared.get(a), work.prepared.get(b))) {
                    work.parent[find(work.parent, a)] = find(work.parent, b);
                }
                if (++work.right >= work.prepared.size()) {
                    work.right = ++work.left + 1;
                }
            } else if (work.grouped < work.prepared.size()) {
                int index = work.grouped++;
                work.members.computeIfAbsent(find(work.parent, index), ignored -> new Members())
                        .add(work.prepared.get(index), lookup);
            } else {
                if (work.output == null) work.output = work.members.values().iterator();
                if (!work.output.hasNext()) return true;
                Members members = work.output.next();
                if (members.recipes.size() > 1) {
                    groups.add(new Group(groups.size() + 1, bucket.type, members.recipes, members.results, members.conflict));
                }
            }
        }
        return work.output != null && !work.output.hasNext();
    }

    private static int find(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    private static boolean sameInputs(Prepared a, Prepared b) {
        if (a.occupied.size() != b.occupied.size()) return false;
        if (a.holder.value() instanceof ShapedRecipe left && b.holder.value() instanceof ShapedRecipe right) {
            if (left.getWidth() != right.getWidth() || left.getHeight() != right.getHeight()) return false;
            return shapedOverlap(a, b, left.getWidth(), false) || shapedOverlap(a, b, left.getWidth(), true);
        }
        int[] matched = new int[b.occupied.size()];
        Arrays.fill(matched, -1);
        for (int i = 0; i < a.occupied.size(); i++) {
            if (!match(a, b, i, matched, new boolean[matched.length])) return false;
        }
        return true;
    }

    private static boolean shapedOverlap(Prepared a, Prepared b, int width, boolean mirrored) {
        for (int i = 0; i < a.ingredients.size(); i++) {
            int j = mirrored ? i / width * width + width - 1 - i % width : i;
            if (!overlaps(a.ingredients.get(i), b.ingredients.get(j))) return false;
        }
        return true;
    }

    private static boolean match(Prepared a, Prepared b, int left, int[] matched, boolean[] seen) {
        for (int right = 0; right < matched.length; right++) {
            if (seen[right] || !overlaps(a.ingredients.get(a.occupied.get(left)), b.ingredients.get(b.occupied.get(right)))) continue;
            seen[right] = true;
            if (matched[right] < 0 || match(a, b, matched[right], matched, seen)) {
                matched[right] = left;
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(Ingredient a, Ingredient b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() == b.isEmpty();
        for (ItemStack stack : a.getItems()) {
            if (b.test(stack)) return true;
        }
        for (ItemStack stack : b.getItems()) {
            if (a.test(stack)) return true;
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
            group.recipes().forEach(holder -> mods.add(holder.id().getNamespace()));
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
                RecipeHolder<?> holder = group.recipes().get(i);
                ItemStack result = group.results().get(i);
                lines.add("    " + holder.id() + "  (" + holder.id().getNamespace() + ")  -> " + result.getCount() + "x " + BuiltInRegistries.ITEM.getKey(result.getItem()));
            }
        }
    }

    public String csv(ReportWriter.Context context) {
        CsvWriter csv = new CsvWriter().comments(context.headerLines());
        csv.row("group", "kind", "recipe_type", "recipe", "mod", "result", "result_count");
        for (Group group : groups) {
            for (int i = 0; i < group.recipes().size(); i++) {
                RecipeHolder<?> holder = group.recipes().get(i);
                ItemStack result = group.results().get(i);
                csv.row(group.id(), group.conflict() ? "conflict" : "duplicate", BuiltInRegistries.RECIPE_TYPE.getKey(group.type()),
                        holder.id(), holder.id().getNamespace(), BuiltInRegistries.ITEM.getKey(result.getItem()), result.getCount());
            }
        }
        return csv.content();
    }
}
