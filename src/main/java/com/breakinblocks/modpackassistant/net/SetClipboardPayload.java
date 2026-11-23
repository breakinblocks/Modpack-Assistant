package com.breakinblocks.modpackassistant.net;

import net.minecraft.network.FriendlyByteBuf;

public record SetClipboardPayload(String text) implements MAPayload {
    public static final int MAX_TEXT_LENGTH = 32_767;

    public static SetClipboardPayload read(FriendlyByteBuf buffer) {
        return new SetClipboardPayload(buffer.readUtf(MAX_TEXT_LENGTH));
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(text, MAX_TEXT_LENGTH);
    }

    @Override
    public void handle() {
        ClientClipboard.set(text);
    }
}
