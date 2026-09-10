package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.config.MAConfig;
import com.breakinblocks.modpackassistant.util.Messages;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DropAccumulator {
    private record Key(Item item, DataComponentPatch components) {}
    public record Drop(ItemStack stack, long count) {}
    private final Map<Key, Drop> drops = new HashMap<>();
    private final int limit;

    public DropAccumulator() {
        this(MAConfig.maxMiningDropVariants());
    }

    public DropAccumulator(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        this.limit = limit;
    }

    public void add(ItemStack stack) {
        if (stack.isEmpty()) return;
        Key key = new Key(stack.getItem(), stack.getComponentsPatch());
        Drop previous = drops.get(key);
        if (previous == null && drops.size() >= limit) throw new IllegalStateException(Messages.MINE_VARIANT_LIMIT.get(limit).getString());
        drops.put(key, new Drop(stack.copyWithCount(1), stack.getCount() + (previous == null ? 0 : previous.count())));
    }

    public List<Drop> ranked() {
        return drops.values().stream().sorted(Comparator.comparingLong(Drop::count).reversed()).toList();
    }

    public long total() {
        return drops.values().stream().mapToLong(Drop::count).sum();
    }
}
