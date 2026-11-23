package com.breakinblocks.modpackassistant.showoff;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.StringRepresentable;

public enum ShowoffSubject implements StringRepresentable {
    STRUCTURE("structure"),
    FILE("file"),
    ENTITY("entity");

    private final String name;

    ShowoffSubject(String name) {
        this.name = name;
    }

    public static ShowoffSubject read(FriendlyByteBuf buffer) {
        return values()[Math.floorMod(buffer.readVarInt(), values().length)];
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(ordinal());
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
