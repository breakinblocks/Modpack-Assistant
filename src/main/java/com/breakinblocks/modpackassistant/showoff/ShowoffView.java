package com.breakinblocks.modpackassistant.showoff;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

public record ShowoffView(float yaw, float pitch, float zoom, float panX, float panY) {
    public static final float DEFAULT_YAW = 45.0F;
    public static final float DEFAULT_PITCH = 35.264F;
    public static final float MIN_PITCH = -90.0F;
    public static final float MAX_PITCH = 90.0F;
    public static final float MIN_ZOOM = 0.1F;
    public static final float MAX_ZOOM = 20.0F;
    public static final float MAX_PAN = 2.0F;

    public static final int ANGLE = 1;
    public static final int ZOOM = 2;
    public static final int PAN = 4;
    public static final int ALL = ANGLE | ZOOM | PAN;

    public static final ShowoffView DEFAULT = new ShowoffView(DEFAULT_YAW, DEFAULT_PITCH, 1.0F, 0.0F, 0.0F);

    public static ShowoffView read(FriendlyByteBuf buffer) {
        return new ShowoffView(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
        buffer.writeFloat(zoom);
        buffer.writeFloat(panX);
        buffer.writeFloat(panY);
    }

    public ShowoffView {
        yaw = Mth.wrapDegrees(yaw);
        pitch = Mth.clamp(pitch, MIN_PITCH, MAX_PITCH);
        zoom = Mth.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        panX = Mth.clamp(panX, -MAX_PAN, MAX_PAN);
        panY = Mth.clamp(panY, -MAX_PAN, MAX_PAN);
    }

    public ShowoffView withAngle(float yaw, float pitch) {
        return new ShowoffView(yaw, pitch, zoom, panX, panY);
    }

    public ShowoffView withZoom(float zoom) {
        return new ShowoffView(yaw, pitch, zoom, panX, panY);
    }

    public ShowoffView withPan(float panX, float panY) {
        return new ShowoffView(yaw, pitch, zoom, panX, panY);
    }

    public ShowoffView merge(ShowoffView other, int mask) {
        return new ShowoffView(
                (mask & ANGLE) != 0 ? other.yaw : yaw,
                (mask & ANGLE) != 0 ? other.pitch : pitch,
                (mask & ZOOM) != 0 ? other.zoom : zoom,
                (mask & PAN) != 0 ? other.panX : panX,
                (mask & PAN) != 0 ? other.panY : panY);
    }
}
