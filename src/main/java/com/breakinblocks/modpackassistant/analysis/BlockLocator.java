package com.breakinblocks.modpackassistant.analysis;

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

public final class BlockLocator {
    public record Hit(BlockPos pos, double distance) {
    }

    private final Block block;
    private final Vec3 origin;
    private final List<Hit> hits = new ArrayList<>();
    private int chunksScanned;

    public BlockLocator(Block block, Vec3 origin) {
        this.block = block;
        this.origin = origin;
    }

    public Block block() {
        return block;
    }

    public int total() {
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
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(block))) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(block)) {
                            BlockPos at = new BlockPos(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z);
                            hits.add(new Hit(at, Math.sqrt(at.distToCenterSqr(origin))));
                        }
                    }
                }
            }
        }
    }

    public List<Hit> nearest() {
        List<Hit> sorted = new ArrayList<>(hits);
        sorted.sort(Comparator.comparingDouble(Hit::distance));
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
