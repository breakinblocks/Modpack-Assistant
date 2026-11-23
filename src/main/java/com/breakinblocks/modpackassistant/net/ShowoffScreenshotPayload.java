package com.breakinblocks.modpackassistant.net;

import com.breakinblocks.modpackassistant.client.showoff.ShowoffClient;
import net.minecraft.network.FriendlyByteBuf;

public record ShowoffScreenshotPayload(String name, int width, int height) implements MAPayload {
    public static ShowoffScreenshotPayload read(FriendlyByteBuf buffer) {
        return new ShowoffScreenshotPayload(buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(name, 256);
        buffer.writeVarInt(width);
        buffer.writeVarInt(height);
    }

    @Override
    public void handle() {
        ShowoffClient.screenshot(name, width, height);
    }
}
