/*
 * Adapted from NTM: Space RenderDropPod and rp_drop_pod OBJ presentation.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: uses Forge 1.20.1 OBJ/vertex consumers and the exact upstream model
 * and texture; the entity state machine remains server-authoritative.
 */
package first.wildfires.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.google.common.collect.ImmutableMap;
import first.wildfires.Wildfires;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import first.wildfires.space.capsule.ReturnCapsuleVisuals;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import org.joml.Matrix4f;

import java.util.Set;

/** Exact NTM: Space reusable drop-pod geometry and skin. */
public final class ReusableReturnCapsuleRenderer extends EntityRenderer<ReusableReturnCapsuleEntity> {

    private static final ResourceLocation TEXTURE = Wildfires.rl(
            "textures/third_party/ntm_space/rp_drop_pod.png");
    private static final Set<String> SURFACE_BODY_COMPONENTS = Set.of("DropPod", "Door", "Legs");
    private static final Set<String> SURFACE_AIRBRAKE_COMPONENT = Set.of("Airbrake0");

    public ReusableReturnCapsuleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.25F;
        shadowStrength = 0.85F;
    }

    @Override
    public void render(ReusableReturnCapsuleEntity capsule, float yaw, float partialTick,
        PoseStack poses, MultiBufferSource buffers, int packedLight) {
        poses.pushPose();
        boolean inOrbit = capsule.level().dimension().equals(SpaceDimensions.ORBIT);
        ReturnCapsuleVisuals.Snapshot visual = ReturnCapsuleVisuals.snapshot(capsule.capsuleState(),
                capsule.phaseTicks() + partialTick, capsule.flightVelocity(), inOrbit);
        // The passenger renderer consumes this exact same conjugation before vanilla body yaw.
        ReturnCapsulePassengerPose.applyAttitude(poses, yaw, visual.pitchDegrees());
        if (visual.orbitRenderAll()) {
            NtmSpaceObjModels.capsule().render(poses, buffers, RenderType::entityCutout,
                    packedLight, OverlayTexture.NO_OVERLAY, partialTick,
                    CompositeRenderable.Transforms.EMPTY);
        } else {
            Set<String> previous = ObjComponentVisibility.enter(SURFACE_BODY_COMPONENTS);
            try {
                    NtmSpaceObjModels.capsule().render(poses, buffers,
                            RenderType::entityCutout, packedLight,
                            OverlayTexture.NO_OVERLAY, partialTick, bodyTransforms(visual));
            } finally {
                ObjComponentVisibility.exit(previous);
            }
            renderSurfaceAirbrakes(poses, buffers, packedLight, partialTick,
                    visual.airbrakeDegrees());
        }
        poses.popPose();
        super.render(capsule, yaw, partialTick, poses, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ReusableReturnCapsuleEntity capsule) {
        return TEXTURE;
    }

    private static CompositeRenderable.Transforms bodyTransforms(ReturnCapsuleVisuals.Snapshot visual) {
        ImmutableMap.Builder<String, Matrix4f> parts = ImmutableMap.builder();
        parts.put("Door", around(0.69291F, 2.8333F, 0.0F,
                0.0F, 0.0F, (float) Math.toRadians(visual.doorDegrees())));
        parts.put("Legs", new Matrix4f().translation(0.0F,
                -0.5F * visual.legExtension(), 0.0F));
        return CompositeRenderable.Transforms.of(parts.build());
    }

    /** Exact RenderDropPod loop: rotate and draw Airbrake0 four times around its original pivot. */
    private static void renderSurfaceAirbrakes(PoseStack poses, MultiBufferSource buffers,
                                                int packedLight, float partialTick,
                                                float brakeDegrees) {
        for (int index = 0; index < 4; index++) {
            poses.pushPose();
            poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(index * 90.0F - 45.0F));
            poses.translate(0.46194D, 3.5D, 0.0D);
            poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(brakeDegrees));
            poses.translate(-0.46194D, -3.5D, 0.0D);
            poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(45.0F));
            Set<String> previous = ObjComponentVisibility.enter(SURFACE_AIRBRAKE_COMPONENT);
            try {
                    NtmSpaceObjModels.capsule().render(poses, buffers,
                            RenderType::entityCutout, packedLight,
                            OverlayTexture.NO_OVERLAY, partialTick,
                            CompositeRenderable.Transforms.EMPTY);
            } finally {
                ObjComponentVisibility.exit(previous);
            }
            poses.popPose();
        }
    }

    private static Matrix4f around(float x, float y, float z, float rx, float ry, float rz) {
        return new Matrix4f().translate(x, y, z).rotateXYZ(rx, ry, rz).translate(-x, -y, -z);
    }

}
