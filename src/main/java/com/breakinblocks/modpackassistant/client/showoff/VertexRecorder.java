package com.breakinblocks.modpackassistant.client.showoff;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor;

import java.util.Arrays;

final class VertexRecorder implements VertexConsumer {
    private static final int INITIAL_CAPACITY = 1024;

    private float[] positions = new float[INITIAL_CAPACITY * 3];
    private float[] normals = new float[INITIAL_CAPACITY * 3];
    private float[] uvs = new float[INITIAL_CAPACITY * 2];
    private int[] colors = new int[INITIAL_CAPACITY];
    private int[] overlays = new int[INITIAL_CAPACITY];
    private int[] lights = new int[INITIAL_CAPACITY];
    private int count;
    private float offsetX;
    private float offsetY;
    private float offsetZ;
    private boolean defaultColorSet;
    private int defaultColor = -1;

    void offset(float x, float y, float z) {
        offsetX = x;
        offsetY = y;
        offsetZ = z;
    }

    boolean isEmpty() {
        return count == 0;
    }

    void replay(VertexConsumer out, PoseStack.Pose pose) {
        for (int i = 0; i < count; i++) {
            int color = colors[i];
            out.vertex(pose.pose(), positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])
                    .color(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color), FastColor.ARGB32.alpha(color))
                    .uv(uvs[i * 2], uvs[i * 2 + 1])
                    .overlayCoords(overlays[i])
                    .uv2(lights[i])
                    .normal(pose.normal(), normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2])
                    .endVertex();
        }
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        if (count == colors.length) {
            grow();
        }
        int index = count++;
        positions[index * 3] = (float) x + offsetX;
        positions[index * 3 + 1] = (float) y + offsetY;
        positions[index * 3 + 2] = (float) z + offsetZ;
        normals[index * 3] = 0.0F;
        normals[index * 3 + 1] = 1.0F;
        normals[index * 3 + 2] = 0.0F;
        uvs[index * 2] = 0.0F;
        uvs[index * 2 + 1] = 0.0F;
        colors[index] = defaultColorSet ? defaultColor : -1;
        overlays[index] = OverlayTexture.NO_OVERLAY;
        lights[index] = LightTexture.FULL_BRIGHT;
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        if (!defaultColorSet) {
            colors[count - 1] = FastColor.ARGB32.color(alpha, red, green, blue);
        }
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        uvs[(count - 1) * 2] = u;
        uvs[(count - 1) * 2 + 1] = v;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        overlays[count - 1] = (u & 0xFFFF) | (v << 16);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        lights[count - 1] = (u & 0xFFFF) | (v << 16);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        normals[(count - 1) * 3] = x;
        normals[(count - 1) * 3 + 1] = y;
        normals[(count - 1) * 3 + 2] = z;
        return this;
    }

    @Override
    public void endVertex() {
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        defaultColorSet = true;
        defaultColor = FastColor.ARGB32.color(alpha, red, green, blue);
    }

    @Override
    public void unsetDefaultColor() {
        defaultColorSet = false;
    }

    private void grow() {
        int capacity = colors.length * 2;
        positions = Arrays.copyOf(positions, capacity * 3);
        normals = Arrays.copyOf(normals, capacity * 3);
        uvs = Arrays.copyOf(uvs, capacity * 2);
        colors = Arrays.copyOf(colors, capacity);
        overlays = Arrays.copyOf(overlays, capacity);
        lights = Arrays.copyOf(lights, capacity);
    }
}
