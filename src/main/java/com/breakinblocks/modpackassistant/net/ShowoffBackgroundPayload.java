package com.breakinblocks.modpackassistant.net;

import com.breakinblocks.modpackassistant.client.showoff.ShowoffClient;
import net.minecraft.network.FriendlyByteBuf;

public record ShowoffBackgroundPayload(int argb) implements MAPayload {
    public static ShowoffBackgroundPayload read(FriendlyByteBuf buffer) {
        return new ShowoffBackgroundPayload(buffer.readInt());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(argb);
    }

    @Override
    public void handle() {
        ShowoffClient.background(argb);
    }
}
