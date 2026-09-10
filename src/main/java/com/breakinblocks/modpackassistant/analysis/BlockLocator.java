package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class BlockLocator {
    public record Hit(BlockPos pos, double distance) {
    }

    private final Block block;
    private final Vec3 origin;
    private static final Comparator<Hit> ORDER = Comparator.comparingDouble(Hit::distance)
            .thenComparingLong(hit -> hit.pos().asLong());
    private final PriorityQueue<Hit> hits = new PriorityQueue<>(ORDER.reversed());
    private final int limit;
    private long matches;
    private int chunksScanned;

    public BlockLocator(Block block, Vec3 origin) {
        this(block, origin, MAConfig.maxLocateResults());
    }

    public BlockLocator(Block block, Vec3 origin, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.block = block;
        this.origin = origin;
        this.limit = limit;
    }

    public Block block() {
        return block;
    }

    public long total() {
        return matches;
    }

    public int retained() {
        return hits.size();
    }

    public int chunksScanned() {
        return chunksScanned;
    }

    public void scanChunk(LevelChunk chunk, ChunkPos pos) {
        chunksScanned++;
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (!section.maybeHas(state -> state.is(block))) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(block)) {
                            BlockPos at = new BlockPos(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z);
                            matches++;
                            Hit hit = new Hit(at, Math.sqrt(at.distToCenterSqr(origin)));
                            if (hits.size() < limit) {
                                hits.add(hit);
                            } else if (ORDER.compare(hit, hits.peek()) < 0) {
                                hits.poll();
                                hits.add(hit);
                            }
                        }
                    }
                }
            }
        }
    }

    public List<Hit> nearest() {
        List<Hit> sorted = new ArrayList<>(hits);
        sorted.sort(ORDER);
        return sorted;
    }

    public String csv(ReportWriter.Context context) {
        CsvWriter csv = new CsvWriter().comments(context.headerLines());
        csv.row("x", "y", "z", "distance");
        for (Hit hit : nearest()) {
            csv.row(hit.pos().getX(), hit.pos().getY(), hit.pos().getZ(), Math.round(hit.distance() * 10D) / 10D);
        }
        return csv.content();
    }
}
