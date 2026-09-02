package com.breakinblocks.modpackassistant.jobs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;

public final class ChunkAccessor {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("modpackassistant", Comparator.comparingLong(ChunkPos::toLong));

    private ChunkAccessor() {
    }

    public static boolean isLoaded(ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().hasChunk(pos.x, pos.z);
    }

    public static int countUnloaded(ServerLevel level, Collection<ChunkPos> chunks) {
        int unloaded = 0;
        for (ChunkPos pos : chunks) {
            if (!isLoaded(level, pos)) {
                unloaded++;
            }
        }
        return unloaded;
    }

    public static <T> T withChunk(ServerLevel level, ChunkPos pos, Function<LevelChunk, T> action) {
        if (isLoaded(level, pos)) {
            return action.apply(level.getChunk(pos.x, pos.z));
        }
        level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
        try {
            return action.apply(level.getChunk(pos.x, pos.z));
        } finally {
            level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
        }
    }
}
