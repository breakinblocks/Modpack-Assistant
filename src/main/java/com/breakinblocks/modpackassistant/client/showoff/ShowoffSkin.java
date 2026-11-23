package com.breakinblocks.modpackassistant.client.showoff;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

record ShowoffSkin(ResourceLocation texture, boolean slim) {
    static final ShowoffSkin DEFAULT = fallback(Util.NIL_UUID);

    static ShowoffSkin fallback(UUID id) {
        return new ShowoffSkin(DefaultPlayerSkin.getDefaultSkin(id), "slim".equals(DefaultPlayerSkin.getSkinModelName(id)));
    }

    static ShowoffSkin fallback(GameProfile profile) {
        return fallback(UUIDUtil.getOrCreatePlayerUUID(profile));
    }

    static CompletableFuture<ShowoffSkin> load(GameProfile profile) {
        CompletableFuture<ShowoffSkin> result = new CompletableFuture<>();
        Minecraft.getInstance().getSkinManager().registerSkins(profile, (type, location, texture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN) {
                result.complete(new ShowoffSkin(location, "slim".equals(texture.getMetadata("model"))));
            }
        }, false);
        return result;
    }
}
