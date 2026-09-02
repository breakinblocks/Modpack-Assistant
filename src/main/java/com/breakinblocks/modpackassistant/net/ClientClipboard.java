package com.breakinblocks.modpackassistant.net;

import net.minecraft.client.Minecraft;

public final class ClientClipboard {
    private ClientClipboard() {
    }

    public static void set(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }
}
