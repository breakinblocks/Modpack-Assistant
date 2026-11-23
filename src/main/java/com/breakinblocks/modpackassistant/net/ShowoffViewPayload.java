package com.breakinblocks.modpackassistant.net;

import com.breakinblocks.modpackassistant.client.showoff.ShowoffClient;
import com.breakinblocks.modpackassistant.showoff.ShowoffView;
import net.minecraft.network.FriendlyByteBuf;

public record ShowoffViewPayload(int mask, ShowoffView view) implements MAPayload {
    public static ShowoffViewPayload read(FriendlyByteBuf buffer) {
        return new ShowoffViewPayload(buffer.readVarInt(), ShowoffView.read(buffer));
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(mask);
        view.write(buffer);
    }

    @Override
    public void handle() {
        ShowoffClient.view(mask, view);
    }
}
