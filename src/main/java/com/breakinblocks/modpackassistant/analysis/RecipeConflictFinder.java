package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
        final List<Prepared> recipes = new ArrayList<>();
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

        final ItemStack result;

        Prepared(RecipeHolder<?> holder, List<Ingredient> ingredients, ItemStack result) {
            this.holder = holder;
            this.ingredients = ingredients;
            this.result = result.copy();
            for (int i = 0; i < ingredients.size(); i++) {
                if (ingredients.get(i) != null) occupied.add(i);
            }
        }
    }

    private static final class Members {
        final List<RecipeHolder<?>> recipes = new ArrayList<>();
        final List<ItemStack> results = new ArrayList<>();
        boolean conflict;

        void add(Prepared prepared) {
            ItemStack result = prepared.result;
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

    private final ContextMap displayContext;
    private final List<Bucket> buckets = new ArrayList<>();
    private final Map<String, Bucket> byKey = new HashMap<>();
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
        recipes.forEach(holder -> addRecipe(holder, filter));
    }

    public void addRecipe(RecipeHolder<?> holder, @Nullable RecipeType<?> filter) {
        Recipe<?> recipe = holder.value();
        if (filter != null && recipe.getType() != filter) return;
        recipeCount++;
        if (recipe.isSpecial()) {
            skipped.add(holder.id().identifier());
            return;
        }
        try {
            List<Ingredient> inputs = ingredients(recipe);
            ItemStack result = resultOf(holder, displayContext);
            if (inputs.isEmpty() || inputs.size() > 81 || result.isEmpty()
                    || inputs.stream().anyMatch(ingredient -> ingredient != null && (!ingredient.isSimple() || ingredient.isEmpty()))) {
                skipped.add(holder.id().identifier());
                return;
            }
            Prepared prepared = new Prepared(holder, inputs, result);
            String key = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()) + "|" + prepared.occupied.size();
            Bucket bucket = byKey.computeIfAbsent(key, ignored -> new Bucket(recipe.getType()));
            bucket.recipes.add(prepared);
            if (bucket.size() == 2) buckets.add(bucket);
        } catch (RuntimeException error) {
            skipped.add(holder.id().identifier());
            ModpackAssistant.LOGGER.debug("Skipped recipe {} without usable static inputs or output", holder.id().identifier(), error);
        }
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
                work.prepared.add(bucket.recipes.get(index));
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
                        .add(work.prepared.get(index));
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
        if (a == null || b == null) return a == b;
        for (var item : a.items().toList()) {
            if (b.acceptsItem(item)) return true;
        }
        return false;
    }

    private static List<Ingredient> ingredients(Recipe<?> recipe) {
        var placement = recipe.placementInfo();
        if (!(recipe instanceof ShapedRecipe)) return placement.ingredients();
        List<Ingredient> grid = new ArrayList<>();
        for (int index : placement.slotsToIngredientIndex()) {
            grid.add(index < 0 ? null : placement.ingredients().get(index));
        }
        return grid;
    }

    private static ItemStack resultOf(RecipeHolder<?> holder, ContextMap displayContext) {
        ItemStack result = ItemStack.EMPTY;
        for (RecipeDisplay display : holder.value().display()) {
            for (ItemStack candidate : display.result().resolveForStacks(displayContext)) {
                if (candidate.isEmpty()) continue;
                if (!result.isEmpty() && (!ItemStack.isSameItemSameComponents(result, candidate)
                        || result.getCount() != candidate.getCount())) return ItemStack.EMPTY;
                result = candidate;
            }
        }
        return result;
    }

    public String log(ReportWriter.Context context) {
        List<String> lines = new ArrayList<>(context.commentLines());
        appendSection(lines, "Conflicts", true);
        appendSection(lines, "Duplicates", false);
        lines.add("");
        lines.add("Skipped dynamic or unsupported recipes (" + skipped.size() + ")");
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
                RecipeHolder<?> holder = group.recipes().get(i);
                ItemStack result = group.results().get(i);
                lines.add("    " + holder.id().identifier() + "  (" + holder.id().identifier().getNamespace() + ")  -> " + result.getCount() + "x " + BuiltInRegistries.ITEM.getKey(result.getItem()));
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
                        holder.id().identifier(), holder.id().identifier().getNamespace(), BuiltInRegistries.ITEM.getKey(result.getItem()), result.getCount());
            }
        }
        return csv.content();
    }
}
