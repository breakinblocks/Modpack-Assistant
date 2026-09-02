package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.jobs.RegionGeometry;
import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OreScan {
    public record Entry(ResourceLocation block, long count, double percent, int lowestY, int highestY, int peakY) {
    }

    private final int minY;
    private final int maxY;
    private final Object2LongOpenHashMap<ResourceLocation> totals = new Object2LongOpenHashMap<>();
    private final Map<ResourceLocation, int[]> perHeight = new HashMap<>();
    private long total;
    private int chunksScanned;

    public OreScan(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public long total() {
        return total;
    }

    public int chunksScanned() {
        return chunksScanned;
    }

    public void scanChunk(LevelChunk chunk, ChunkPos pos) {
        chunksScanned++;
        RegionGeometry.forEachBlock(pos, minY, maxY, false, blockPos -> {
            BlockState state = chunk.getBlockState(blockPos);
            if (state.isAir() || !state.is(Tags.Blocks.ORES)) {
                return;
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            totals.addTo(key, 1L);
            perHeight.computeIfAbsent(key, ignored -> new int[maxY - minY + 1])[blockPos.getY() - minY]++;
            total++;
        });
    }

    public List<Entry> ranked() {
        List<Entry> entries = new ArrayList<>();
        for (var entry : totals.object2LongEntrySet()) {
            int[] heights = perHeight.get(entry.getKey());
            int lowest = -1;
            int highest = -1;
            int peak = 0;
            for (int i = 0; i < heights.length; i++) {
                if (heights[i] == 0) {
                    continue;
                }
                if (lowest < 0) {
                    lowest = i;
                }
                highest = i;
                if (heights[i] > heights[peak]) {
                    peak = i;
                }
            }
            double percent = total == 0 ? 0.0D : entry.getLongValue() * 100.0D / total;
            entries.add(new Entry(entry.getKey(), entry.getLongValue(), percent, lowest + minY, highest + minY, peak + minY));
        }
        entries.sort(Comparator.comparingLong(Entry::count).reversed().thenComparing(entry -> entry.block().toString()));
        return entries;
    }

    public String summaryCsv(ReportWriter.Context context) {
        CsvWriter csv = new CsvWriter().comments(context.headerLines());
        csv.row("block", "mod", "count", "percent", "blocks_per_chunk", "lowest_y", "highest_y", "peak_y");
        for (Entry entry : ranked()) {
            csv.row(entry.block(), entry.block().getNamespace(), entry.count(),
                    String.format("%.4f", entry.percent()),
                    String.format("%.3f", chunksScanned == 0 ? 0.0D : (double) entry.count() / chunksScanned),
                    entry.lowestY(), entry.highestY(), entry.peakY());
        }
        return csv.content();
    }

    public String perHeightCsv(ReportWriter.Context context) {
        List<Entry> ranked = ranked();
        CsvWriter csv = new CsvWriter().comments(context.headerLines());
        List<Object> header = new ArrayList<>();
        header.add("y");
        for (Entry entry : ranked) {
            header.add(entry.block().toString());
        }
        csv.row(header);
        for (int y = minY; y <= maxY; y++) {
            List<Object> row = new ArrayList<>();
            row.add(y);
            for (Entry entry : ranked) {
                row.add(perHeight.get(entry.block())[y - minY]);
            }
            csv.row(row);
        }
        return csv.content();
    }
}
