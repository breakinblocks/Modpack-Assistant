package com.breakinblocks.modpackassistant.jobs;

import com.breakinblocks.modpackassistant.ModpackAssistant;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

@EventBusSubscriber(modid = ModpackAssistant.MOD_ID)
public final class EntityIndex {
    private static final Map<ServerLevel, NavigableMap<Integer, UUID>> LEVELS = new WeakHashMap<>();

    private EntityIndex() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Entity entity = event.getEntity();
            LEVELS.computeIfAbsent(level, ignored -> new TreeMap<>()).put(entity.getId(), entity.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NavigableMap<Integer, UUID> entities = LEVELS.get(level);
            if (entities != null) entities.remove(event.getEntity().getId(), event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        LEVELS.clear();
    }

    public static Cursor cursor(ServerLevel level) {
        return new Cursor(level, LEVELS.computeIfAbsent(level, ignored -> new TreeMap<>()));
    }

    public static final class Cursor {
        private final ServerLevel level;
        private final NavigableMap<Integer, UUID> entities;
        private final int last;
        private Integer current;

        private Cursor(ServerLevel level, NavigableMap<Integer, UUID> entities) {
            this.level = level;
            this.entities = entities;
            last = entities.isEmpty() ? Integer.MIN_VALUE : entities.lastKey();
        }

        public boolean step(int budget, Consumer<Entity> action) {
            if (budget < 1) throw new IllegalArgumentException("budget must be positive");
            for (int i = 0; i < budget; i++) {
                Map.Entry<Integer, UUID> entry = current == null ? entities.firstEntry() : entities.higherEntry(current);
                if (entry == null || entry.getKey() > last) return true;
                current = entry.getKey();
                Entity entity = level.getEntity(entry.getValue());
                if (entity != null && entity.getId() == current && !entity.isRemoved()) action.accept(entity);
            }
            Integer next = entities.higherKey(current);
            return next == null || next > last;
        }
    }
}
