package com.breakinblocks.modpackassistant.analysis;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LootContexts {
    public record Built(LootParams params, List<String> missing) {
        public boolean ok() {
            return missing.isEmpty();
        }
    }

    private LootContexts() {
    }

    public static Built build(ServerLevel level, BlockPos origin, ServerPlayer player, float luck, ContextKeySet paramSet) {
        Set<ContextKey<?>> allowed = paramSet.allowed();
        LootParams.Builder builder = new LootParams.Builder(level).withLuck(luck);
        Set<ContextKey<?>> supplied = new HashSet<>();
        if (allowed.contains(LootContextParams.ORIGIN)) {
            builder.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(origin));
            supplied.add(LootContextParams.ORIGIN);
        }
        if (allowed.contains(LootContextParams.THIS_ENTITY)) {
            builder.withParameter(LootContextParams.THIS_ENTITY, player);
            supplied.add(LootContextParams.THIS_ENTITY);
        }
        List<String> missing = new ArrayList<>();
        for (ContextKey<?> required : paramSet.required()) {
            if (!supplied.contains(required)) {
                missing.add(required.name().toString());
            }
        }
        if (!missing.isEmpty()) {
            return new Built(null, missing);
        }
        return new Built(builder.create(paramSet), missing);
    }
}
