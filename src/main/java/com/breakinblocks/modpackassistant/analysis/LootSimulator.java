package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.util.Messages;
import com.breakinblocks.modpackassistant.commands.items.ItemStrings;
import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootContext;
import java.util.Optional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LootSimulator {
    public static final int BATCH = 5_000;
    private static final double RARE_PERCENT = 1.0D;
    private static final double COMMON_PERCENT = 99.0D;

    public static final class Stat {
        public final String item;
        public final String components;
        public int appearances;
        public long total;
        public int min = Integer.MAX_VALUE;
        public int max;

        Stat(String item, String components) {
            this.item = item;
            this.components = components;
        }

        public double dropChance(int iterations) {
            return iterations == 0 ? 0.0D : appearances * 100.0D / iterations;
        }

        public double meanPerRoll(int iterations) {
            return iterations == 0 ? 0.0D : (double) total / iterations;
        }

        public double meanPerAppearance() {
            return appearances == 0 ? 0.0D : (double) total / appearances;
        }

        public String outlier(int iterations) {
            double chance = dropChance(iterations);
            if (chance < RARE_PERCENT) {
                return "rare";
            }
            if (chance > COMMON_PERCENT) {
                return "common";
            }
            return "";
        }
    }

    private final LootTable table;
    private final LootParams params;
    private final HolderLookup.Provider lookup;
    private final int iterations;
    private final RandomSource random = RandomSource.create();
    private final Map<String, Stat> stats = new LinkedHashMap<>();
    private final Int2IntOpenHashMap itemsPerRoll = new Int2IntOpenHashMap();
    private int rolled;
    private int emptyRolls;
    private long totalItems;

    public LootSimulator(LootTable table, LootParams params, HolderLookup.Provider lookup, int iterations) {
        this.table = table;
        this.params = params;
        this.lookup = lookup;
        this.iterations = iterations;
    }

    public int iterations() {
        return iterations;
    }

    public int rolled() {
        return rolled;
    }

    public int emptyRolls() {
        return emptyRolls;
    }

    public double emptyPercent() {
        return rolled == 0 ? 0.0D : emptyRolls * 100.0D / rolled;
    }

    public void rollBatch() {
        rollBatch(BATCH);
    }

    public void rollBatch(int budget) {
        int end = Math.min(iterations, rolled + budget);
        Map<String, Integer> perRoll = new LinkedHashMap<>();
        while (rolled < end) {
            perRoll.clear();
            List<ItemStack> drops = new ArrayList<>();
            table.getRandomItemsRaw(new LootContext.Builder(params).withOptionalRandomSource(random).create(Optional.empty()), stack -> {
                if (drops.size() >= 10_000) throw new IllegalStateException(Messages.LOOT_OUTPUT_LIMIT.get().getString());
                drops.add(stack);
            });
            int count = 0;
            for (ItemStack stack : drops) {
                if (stack.isEmpty()) {
                    continue;
                }
                count = Math.addExact(count, stack.getCount());
                String key = ItemStrings.giveString(stack, lookup);
                perRoll.merge(key, stack.getCount(), Math::addExact);
            }
            for (Map.Entry<String, Integer> entry : perRoll.entrySet()) {
                Stat stat = stats.computeIfAbsent(entry.getKey(), key -> {
                    if (stats.size() >= 10_000) throw new IllegalStateException(Messages.LOOT_OUTPUT_LIMIT.get().getString());
                    int bracket = key.indexOf('[');
                    return new Stat(bracket < 0 ? key : key.substring(0, bracket), bracket < 0 ? "" : key.substring(bracket));
                });
                stat.appearances++;
                stat.total += entry.getValue();
                stat.min = Math.min(stat.min, entry.getValue());
                stat.max = Math.max(stat.max, entry.getValue());
            }
            if (count == 0) {
                emptyRolls++;
            }
            totalItems += count;
            itemsPerRoll.addTo(count, 1);
            rolled++;
        }
    }

    public List<Stat> ranked() {
        List<Stat> list = new ArrayList<>(stats.values());
        list.sort(Comparator.comparingInt((Stat stat) -> stat.appearances).reversed().thenComparing(stat -> stat.item));
        return list;
    }

    public double meanItemsPerRoll() {
        return rolled == 0 ? 0.0D : (double) totalItems / rolled;
    }

    public int medianItemsPerRoll() {
        if (rolled == 0) {
            return 0;
        }
        int[] keys = itemsPerRoll.keySet().toIntArray();
        Arrays.sort(keys);
        long seen = 0;
        for (int key : keys) {
            seen += itemsPerRoll.get(key);
            if (seen * 2 >= rolled) {
                return key;
            }
        }
        return keys[keys.length - 1];
    }

    public String csv(ReportWriter.Context context) {
        List<Stat> ranked = ranked();
        CsvWriter csv = new CsvWriter().comments(context.headerLines())
                .comment("rolls: " + rolled)
                .comment("empty_rolls: " + emptyRolls + " (" + String.format("%.4f", emptyPercent()) + "%)")
                .comment("mean_items_per_roll: " + String.format("%.4f", meanItemsPerRoll()))
                .comment("median_items_per_roll: " + medianItemsPerRoll())
                .comment("rare_outliers (under " + RARE_PERCENT + "%): " + names(ranked, "rare"))
                .comment("common_outliers (over " + COMMON_PERCENT + "%): " + names(ranked, "common"));
        csv.row("item", "components", "appearances", "drop_chance_percent", "total_count", "mean_per_roll", "mean_per_appearance", "min_stack", "max_stack", "outlier");
        for (Stat stat : ranked) {
            csv.row(stat.item, stat.components, stat.appearances, String.format("%.4f", stat.dropChance(rolled)), stat.total,
                    String.format("%.4f", stat.meanPerRoll(rolled)), String.format("%.4f", stat.meanPerAppearance()),
                    stat.min == Integer.MAX_VALUE ? 0 : stat.min, stat.max, stat.outlier(rolled));
        }
        return csv.content();
    }

    private String names(List<Stat> ranked, String outlier) {
        List<String> names = new ArrayList<>();
        for (Stat stat : ranked) {
            if (stat.outlier(rolled).equals(outlier)) {
                names.add(stat.item + stat.components);
            }
        }
        return names.isEmpty() ? "none" : String.join("; ", names);
    }
}
