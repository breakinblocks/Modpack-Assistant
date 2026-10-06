package com.breakinblocks.modpackassistant.client.showoff;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;

final class ShowoffPlayerRenderer extends PlayerRenderer {
    ShowoffPlayerRenderer(EntityRendererProvider.Context context, boolean slim) {
        super(context, slim);
        model = new PosedModel(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
    }

    @Override
    protected boolean shouldShowName(AbstractClientPlayer entity) {
        return false;
    }

    private static final class PosedModel extends PlayerModel<AbstractClientPlayer> {
        private PosedModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(AbstractClientPlayer entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            if (!(entity instanceof ShowoffPlayer player)) {
                return;
            }
            float[][] rotations = player.rotations();
            apply(head, rotations[0]);
            apply(body, rotations[1]);
            apply(leftArm, rotations[2]);
            apply(rightArm, rotations[3]);
            apply(leftLeg, rotations[4]);
            apply(rightLeg, rotations[5]);
            hat.copyFrom(head);
            jacket.copyFrom(body);
            leftSleeve.copyFrom(leftArm);
            rightSleeve.copyFrom(rightArm);
            leftPants.copyFrom(leftLeg);
            rightPants.copyFrom(rightLeg);
        }

        private static void apply(ModelPart part, float[] angles) {
            part.xRot = angles[0] * Mth.DEG_TO_RAD;
            part.yRot = angles[1] * Mth.DEG_TO_RAD;
            part.zRot = angles[2] * Mth.DEG_TO_RAD;
        }
    }
}
