package com.breakinblocks.modpackassistant.jobs;

import com.breakinblocks.modpackassistant.util.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public record RegionGeometry(ChunkPos center, int radius) {
    public int span() {
        return 2 * radius + 1;
    }

    public int chunkCount() {
        return span() * span();
    }

    public List<ChunkPos> chunks() {
        List<ChunkPos> chunks = new ArrayList<>(chunkCount());
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    public MutableComponent spanText() {
        return Messages.REGION_SPAN.get(span(), span());
    }

    public static int minY(LevelHeightAccessor level) {
        return level.getMinBuildHeight();
    }

    public static int maxY(LevelHeightAccessor level) {
        return level.getMaxBuildHeight() - 1;
    }

    public static void forEachBlock(ChunkPos chunk, int minY, int maxY, boolean topDown, Consumer<BlockPos> consumer) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int startY = topDown ? maxY : minY;
        int endY = topDown ? minY : maxY;
        int step = topDown ? -1 : 1;
        for (int y = startY; topDown ? y >= endY : y <= endY; y += step) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    consumer.accept(pos.set(x, y, z));
                }
            }
        }
    }
}
