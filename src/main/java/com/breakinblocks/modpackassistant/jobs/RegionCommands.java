package com.breakinblocks.modpackassistant.jobs;

import com.breakinblocks.modpackassistant.commands.CommandResults;
import com.breakinblocks.modpackassistant.util.Messages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class RegionCommands {
    private RegionCommands() {
    }

    @Nullable
    public static RegionGeometry region(CommandSourceStack source, ServerPlayer player, int radius, int maximum, String configKey) {
        if (radius > maximum) {
            int span = 2 * radius + 1;
            CommandResults.fail(source, Messages.RADIUS_TOO_LARGE.get(radius, maximum, span * span, configKey));
            return null;
        }
        return new RegionGeometry(player.chunkPosition(), radius);
    }

    public static void reportUnloaded(Run run, ServerLevel level, RegionGeometry region) {
        int unloaded = ChunkAccessor.countUnloaded(level, region.chunks());
        if (unloaded > 0) {
            run.message(Messages.RUN_UNLOADED.get(unloaded, region.chunkCount()));
        }
    }
}
