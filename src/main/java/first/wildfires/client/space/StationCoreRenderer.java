/*
 * Adapted from NTM: Space RenderOrbitalStation and docking_port OBJ presentation.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: renders the exact upstream model through Forge 1.20.1 and uniformly
 * scales it to the requested three-by-three-by-two immutable occupied volume.
 */
package first.wildfires.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.space.content.StationCoreBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.client.model.renderable.CompositeRenderable;

/** One renderer at the core anchor draws the complete NTM docking-core model once. */
public final class StationCoreRenderer implements BlockEntityRenderer<StationCoreBlockEntity> {

    private static final float SCALE = 0.52F;
    private static final float MIN_Y = -2.842071F;

    public StationCoreRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StationCoreBlockEntity core, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        poses.pushPose();
        poses.translate(0.5D, -MIN_Y * SCALE, 0.5D);
        poses.scale(SCALE, SCALE, SCALE);
        NtmSpaceObjModels.stationCore().render(poses, buffers, RenderType::entityCutoutNoCull,
                packedLight, OverlayTexture.NO_OVERLAY, partialTick, CompositeRenderable.Transforms.EMPTY);
        poses.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(StationCoreBlockEntity core) {
        return true;
    }

}
