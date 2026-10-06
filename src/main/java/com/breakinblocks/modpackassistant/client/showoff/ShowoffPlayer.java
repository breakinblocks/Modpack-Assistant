package com.breakinblocks.modpackassistant.client.showoff;

import com.mojang.authlib.GameProfile;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;

final class ShowoffPlayer extends RemotePlayer {
    private static final byte ALL_PARTS = 0x7F;

    private final float[][] rotations;
    private PlayerSkin skin = DefaultPlayerSkin.get(Util.NIL_UUID);

    ShowoffPlayer(ClientLevel level, float[][] rotations) {
        super(level, new GameProfile(Util.NIL_UUID, "Showoff"));
        this.rotations = rotations;
        entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, ALL_PARTS);
        moveTo(0.0, 0.0, 0.0, 0.0F, 0.0F);
        setYBodyRot(0.0F);
        setYHeadRot(0.0F);
        yBodyRotO = 0.0F;
        yHeadRotO = 0.0F;
    }

    float[][] rotations() {
        return rotations;
    }

    void skin(PlayerSkin skin) {
        this.skin = skin;
    }

    @Override
    public PlayerSkin getSkin() {
        return skin;
    }
}
