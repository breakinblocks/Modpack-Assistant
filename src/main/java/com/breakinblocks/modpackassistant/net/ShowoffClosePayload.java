package com.breakinblocks.modpackassistant.net;

import com.breakinblocks.modpackassistant.client.showoff.ShowoffClient;
import net.minecraft.network.FriendlyByteBuf;

public record ShowoffClosePayload() implements MAPayload {
    public static final ShowoffClosePayload INSTANCE = new ShowoffClosePayload();

    @Override
    public void write(FriendlyByteBuf buffer) {
    }

    @Override
    public void handle() {
        ShowoffClient.close();
    }
}
