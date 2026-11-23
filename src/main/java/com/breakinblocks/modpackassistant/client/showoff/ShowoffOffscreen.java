package com.breakinblocks.modpackassistant.client.showoff;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

final class ShowoffOffscreen {
    static final Vector4fc CLEAR_COLOR = new Vector4f(0.0F);
    static final double CLEAR_DEPTH = 0.0;

    private ShowoffOffscreen() {
    }

    static void render(String label, GpuTextureView color, GpuTextureView depth, Consumer<SubmitNodeCollector> submit) {
        SubmitNodeStorage storage = new SubmitNodeStorage();
        submit.accept(storage);
        FeatureRenderDispatcher dispatcher = Minecraft.getInstance().gameRenderer.featureRenderDispatcher();
        try (
                FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(storage);
                RenderPass pass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(() -> label, color, Optional.empty(), depth, OptionalDouble.empty())
        ) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        }
    }
}
