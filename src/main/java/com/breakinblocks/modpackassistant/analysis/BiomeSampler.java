package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BiomeSampler {
    public record Entry(ResourceLocation biome, int samples, double percent, BlockPos nearest, double distance) {
    }

    private final BlockPos center;
    private final int radius;
    private final int interval;
    private final int y;
    private final BiomeSource source;
    private final Climate.Sampler sampler;
    private final Object2IntOpenHashMap<ResourceLocation> counts = new Object2IntOpenHashMap<>();
    private final Map<ResourceLocation, BlockPos> nearest = new HashMap<>();
    private int samples;

    public BiomeSampler(ServerLevel level, BlockPos center, int radius, int interval, int y) {
        this.center = center;
        this.radius = radius;
        this.interval = interval;
        this.y = y;
        this.source = level.getChunkSource().getGenerator().getBiomeSource();
        this.sampler = level.getChunkSource().randomState().sampler();
    }

    public int pointsPerAxis() {
        return 2 * radius / interval + 1;
    }

    public long expectedSamples() {
        return (long) pointsPerAxis() * pointsPerAxis();
    }

    public int samples() {
        return samples;
    }

    public int distinctBiomes() {
        return counts.size();
    }

    public List<Integer> rows() {
        List<Integer> rows = new ArrayList<>();
        for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += interval) {
            rows.add(z);
        }
        return rows;
    }

    public void sampleRow(int z) {
        for (int x = center.getX() - radius; x <= center.getX() + radius; x += interval) {
            Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
            ResourceLocation id = biome.unwrapKey().map(ResourceKey::location).orElse(ResourceLocation.withDefaultNamespace("unknown"));
            counts.addTo(id, 1);
            samples++;
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos current = nearest.get(id);
            if (current == null || pos.distSqr(center) < current.distSqr(center)) {
                nearest.put(id, pos);
            }
        }
    }

    public List<Entry> ranked() {
        List<Entry> entries = new ArrayList<>();
        for (var entry : counts.object2IntEntrySet()) {
            BlockPos pos = nearest.get(entry.getKey());
            entries.add(new Entry(entry.getKey(), entry.getIntValue(), samples == 0 ? 0.0D : entry.getIntValue() * 100.0D / samples, pos, Math.sqrt(pos.distSqr(center))));
        }
        entries.sort(Comparator.comparingInt(Entry::samples).reversed().thenComparing(entry -> entry.biome().toString()));
        return entries;
    }

    public String csv(ReportWriter.Context context) {
        CsvWriter csv = new CsvWriter().comments(context.headerLines());
        csv.row("biome", "samples", "percent", "nearest_x", "nearest_y", "nearest_z", "nearest_distance");
        for (Entry entry : ranked()) {
            csv.row(entry.biome(), entry.samples(), String.format("%.4f", entry.percent()),
                    entry.nearest().getX(), entry.nearest().getY(), entry.nearest().getZ(), String.format("%.1f", entry.distance()));
        }
        return csv.content();
    }
}
