package com.breakinblocks.modpackassistant.showoff;

import net.minecraft.world.scores.TeamColor;

import java.util.List;

public final class ShowoffBackground {
    public static final int TRANSPARENT = 0;
    public static final int DEFAULT = 0xFF000000;
    public static final List<TeamColor> SWATCHES = TeamColor.VALUES;

    private ShowoffBackground() {
    }

    public static int of(TeamColor color) {
        return opaque(color.rgb());
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    public static boolean isTransparent(int argb) {
        return (argb >>> 24) == 0;
    }

    public static String describe(int argb) {
        if (isTransparent(argb)) {
            return "transparent";
        }
        for (TeamColor swatch : SWATCHES) {
            if (of(swatch) == argb) {
                return swatch.getSerializedName();
            }
        }
        return String.format("#%06X", argb & 0xFFFFFF);
    }
}
