package com.breakinblocks.modpackassistant.jobs;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collection;
import java.util.function.Function;

public final class ChunkAccessor {
    public static final DeferredRegister<TicketType> TICKET_TYPES = DeferredRegister.create(BuiltInRegistries.TICKET_TYPE, ModpackAssistant.MOD_ID);

    private static final DeferredHolder<TicketType, TicketType> TICKET =
            TICKET_TYPES.register("scan", () -> new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING));

    private ChunkAccessor() {
    }

    public static boolean isLoaded(ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().hasChunk(pos.x(), pos.z());
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
            return action.apply(level.getChunk(pos.x(), pos.z()));
        }
        level.getChunkSource().addTicketWithRadius(TICKET.get(), pos, 0);
        try {
            return action.apply(level.getChunk(pos.x(), pos.z()));
        } finally {
            level.getChunkSource().removeTicketWithRadius(TICKET.get(), pos, 0);
        }
    }
}
