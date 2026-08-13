/*
 * Adapted from NTM: Space RenderDropPod and rp_drop_pod OBJ presentation.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: uses Forge 1.20.1 OBJ/vertex consumers and the exact upstream model
 * and texture; the entity state machine remains server-authoritative.
 */
package first.wildfires.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import first.wildfires.Wildfires;
import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.renderable.CompositeRenderable;

/** Exact NTM: Space reusable drop-pod geometry and skin. */
public final class ReusableReturnCapsuleRenderer extends EntityRenderer<ReusableReturnCapsuleEntity> {

    private static final ResourceLocation TEXTURE = Wildfires.rl(
            "textures/third_party/ntm_space/rp_drop_pod.png");

    public ReusableReturnCapsuleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.25F;
        shadowStrength = 0.85F;
    }

    @Override
    public void render(ReusableReturnCapsuleEntity capsule, float yaw, float partialTick,
                       PoseStack poses, MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        NtmSpaceObjModels.capsule().render(poses, buffers, RenderType::entityCutoutNoCull,
                packedLight, OverlayTexture.NO_OVERLAY, partialTick, CompositeRenderable.Transforms.EMPTY);
        poses.popPose();
        super.render(capsule, yaw, partialTick, poses, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ReusableReturnCapsuleEntity capsule) {
        return TEXTURE;
    }
}
