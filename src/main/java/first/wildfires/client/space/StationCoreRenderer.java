/*
 * Adapted from NTM: Space RenderOrbitalStation and docking_port OBJ presentation.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: renders the exact upstream model through Forge 1.20.1 while the
 * five-by-five-by-two proxy structure remains the logical ownership/collision contract.
 */
package first.wildfires.client.space;

import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.space.content.StationCoreBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.Set;

/** One renderer at the core anchor draws the complete NTM docking-core model once. */
public final class StationCoreRenderer implements BlockEntityRenderer<StationCoreBlockEntity> {

    private static final Set<String> PORT_COMPONENT = Set.of("Port");
    private static final Set<String> ARM_COMPONENT = Set.of("ArmZP");

    public StationCoreRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StationCoreBlockEntity core, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        int environmentLight = core.getLevel() == null ? packedLight
                : LevelRenderer.getLightColor(core.getLevel(), core.getBlockPos().above(2));
        poses.pushPose();
        // RenderOrbitalStation uses the OBJ at its authored 1:1 scale and anchors it at block Y+1.
        // The old 0.52 fit-to-proxy transform made the five-unit port barely wider than the
        // two-unit return pod and destroyed NTM's intended core/pod proportion.
        poses.translate(0.5D, 1.0D, 0.5D);
        Set<String> previous = ObjComponentVisibility.enter(PORT_COMPONENT);
        try {
                NtmSpaceObjModels.stationCore().render(poses, buffers,
                        RenderType::entityCutoutNoCull, environmentLight,
                        OverlayTexture.NO_OVERLAY, partialTick,
                        net.minecraftforge.client.model.renderable.CompositeRenderable.Transforms.EMPTY);
        } finally {
            ObjComponentVisibility.exit(previous);
        }
        renderArms(poses, buffers, environmentLight, partialTick,
                core.clientArmRotation(partialTick));
        poses.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(StationCoreBlockEntity core) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    /** Exact RenderOrbitalStation loop: one ArmZP mesh, rotated around the port four times. */
    private static void renderArms(PoseStack poses, MultiBufferSource buffers, int packedLight,
                                   float partialTick, float armDegrees) {
        for (int index = 0; index < 4; index++) {
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(index * 90.0F));
            poses.translate(0.0D, -1.75D, -2.0D);
            poses.mulPose(Axis.XP.rotationDegrees(-armDegrees));
            poses.translate(0.0D, 1.75D, 2.0D);
            Set<String> previous = ObjComponentVisibility.enter(ARM_COMPONENT);
            try {
                    NtmSpaceObjModels.stationCore().render(poses, buffers,
                            RenderType::entityCutoutNoCull, packedLight,
                            OverlayTexture.NO_OVERLAY, partialTick,
                            net.minecraftforge.client.model.renderable.CompositeRenderable.Transforms.EMPTY);
            } finally {
                ObjComponentVisibility.exit(previous);
            }
            poses.popPose();
        }
    }
}
