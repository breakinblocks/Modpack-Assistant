package com.breakinblocks.modpackassistant.client.barriers;

import com.breakinblocks.modpackassistant.config.MAClientConfig;
import net.minecraft.client.Minecraft;

public final class BarrierDisplay {
    private BarrierDisplay() {
    }

    public static boolean enabled() {
        return MAClientConfig.SPEC.isLoaded() && MAClientConfig.SHOW_BARRIERS.get();
    }

    public static void set(boolean shown) {
        if (MAClientConfig.SPEC.isLoaded() && MAClientConfig.SHOW_BARRIERS.get() != shown) {
            MAClientConfig.SHOW_BARRIERS.set(shown);
            MAClientConfig.SHOW_BARRIERS.save();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.levelRenderer.allChanged();
        }
    }
}
