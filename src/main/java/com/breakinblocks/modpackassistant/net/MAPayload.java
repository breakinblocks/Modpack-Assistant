package com.breakinblocks.modpackassistant.net;

import net.minecraft.network.FriendlyByteBuf;

public interface MAPayload {
    void write(FriendlyByteBuf buffer);

    void handle();
}
