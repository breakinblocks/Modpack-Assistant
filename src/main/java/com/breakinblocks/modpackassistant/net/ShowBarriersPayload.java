package com.breakinblocks.modpackassistant.net;

import com.breakinblocks.modpackassistant.client.barriers.BarrierDisplay;
import net.minecraft.network.FriendlyByteBuf;

public record ShowBarriersPayload(boolean shown) implements MAPayload {
    public static ShowBarriersPayload read(FriendlyByteBuf buffer) {
        return new ShowBarriersPayload(buffer.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(shown);
    }

    @Override
    public void handle() {
        BarrierDisplay.set(shown);
    }
}
