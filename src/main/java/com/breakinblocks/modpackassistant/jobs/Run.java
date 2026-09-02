package com.breakinblocks.modpackassistant.jobs;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import com.breakinblocks.modpackassistant.util.Messages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class Run {
    private static final AtomicInteger IDS = new AtomicInteger();

    private final int id = IDS.incrementAndGet();
    private final CommandSourceStack source;
    @Nullable
    private final UUID ownerId;
    private final String ownerName;
    private final String description;
    private final ResourceKey<Level> dimension;
    private final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
    private int total;
    private int done;
    private int lastDecile;
    private long startedNanos;
    private Consumer<Run> onComplete = run -> {};
    private Consumer<Run> onCancel = run -> {};

    public Run(CommandSourceStack source, String description, ResourceKey<Level> dimension) {
        this.source = source;
        this.description = description;
        this.dimension = dimension;
        if (source.getEntity() instanceof ServerPlayer player) {
            this.ownerId = player.getUUID();
            this.ownerName = player.getGameProfile().getName();
        } else {
            this.ownerId = null;
            this.ownerName = source.getTextName();
        }
    }

    public Run job(Runnable job) {
        jobs.add(job);
        total++;
        return this;
    }

    public Run onComplete(Consumer<Run> callback) {
        this.onComplete = callback;
        return this;
    }

    public Run onCancel(Consumer<Run> callback) {
        this.onCancel = callback;
        return this;
    }

    public int id() {
        return id;
    }

    public String description() {
        return description;
    }

    public String ownerName() {
        return ownerName;
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public CommandSourceStack source() {
        return source;
    }

    public int total() {
        return total;
    }

    public int done() {
        return done;
    }

    public int remaining() {
        return jobs.size();
    }

    public int percent() {
        return total == 0 ? 100 : done * 100 / total;
    }

    public String elapsed() {
        return formatSeconds((System.nanoTime() - startedNanos) / 1_000_000_000L);
    }

    public static String formatSeconds(long seconds) {
        if (seconds < 1) {
            return "under a second";
        }
        return DurationFormatUtils.formatDurationWords(seconds * 1000L, true, true);
    }

    public void message(Component text) {
        MinecraftServer server = source.getServer();
        if (ownerId != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
            if (player != null) {
                player.sendSystemMessage(text);
            } else {
                ModpackAssistant.LOGGER.info("[run #{}] {}", id, text.getString());
            }
            return;
        }
        source.sendSystemMessage(text);
    }

    void start() {
        startedNanos = System.nanoTime();
    }

    @Nullable
    Runnable nextJob() {
        return jobs.poll();
    }

    void markDone() {
        done++;
        int decile = total == 0 ? 10 : done * 10 / total;
        if (decile > lastDecile && decile < 10) {
            lastDecile = decile;
            message(Messages.RUN_PROGRESS.get(id, percent(), done, total));
        }
    }

    void complete() {
        onComplete.accept(this);
        message(Messages.RUN_FINISHED.get(id, elapsed()));
    }

    void cancel() {
        jobs.clear();
        onCancel.accept(this);
    }

    void fail(Throwable error) {
        jobs.clear();
        ModpackAssistant.LOGGER.error("Run #{} ({}) failed after {} of {} jobs", id, description, done, total, error);
        message(Messages.RUN_FAILED.get(id, done, total, String.valueOf(error.getMessage())));
        onCancel.accept(this);
    }
}
