/*
 * Adapted from Waterfall effect presentation and KSPIE engine configurations.
 * Copyright Waterfall and KSP Interstellar Extended contributors.
 * SPDX-License-Identifier: CC-BY-NC-SA-4.0
 */
package first.wildfires.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.Wildfires;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.content.WaterfallTestEngineBlockEntity;
import first.wildfires.space.content.WaterfallTestEngineVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Defers the translated Daedalus plume until buffered station geometry has written depth. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT)
public final class WaterfallTestEngineRenderer
        implements BlockEntityRenderer<WaterfallTestEngineBlockEntity> {

    private static final List<DeferredEngine> DEFERRED = new ArrayList<>();

    public WaterfallTestEngineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WaterfallTestEngineBlockEntity engine, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = engine.getLevel();
        if (level == null || level.dimension() != SpaceDimensions.ORBIT || engine.output() <= 0) return;
        DEFERRED.add(new DeferredEngine(level, engine.getBlockPos().immutable(),
                level.getGameTime() + (double) partialTick, engine.output() / 100.0F,
                engine.getBlockPos().hashCode(), engine.variant()));
    }

    @SubscribeEvent
    public static void renderDeferred(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || level.dimension() != SpaceDimensions.ORBIT) {
            DEFERRED.clear();
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        try {
            for (DeferredEngine engine : DEFERRED) {
                if (engine.level != level) continue;
                poses.pushPose();
                try {
                    poses.translate(engine.position.getX() + 0.5D - camera.x,
                            engine.position.getY() + 0.5D - camera.y,
                            engine.position.getZ() + 1.001D - camera.z);
                    float cameraX = (float) (camera.x - engine.position.getX() - 0.5D);
                    float cameraY = (float) (camera.y - engine.position.getY() - 0.5D);
                    float cameraZ = (float) (camera.z - engine.position.getZ() - 1.001D);
                    WaterfallTranslatedEngineShader.render(poses, engine.variant,
                            engine.gameTime, engine.throttle,
                            engine.variationSeed, cameraX, cameraY, cameraZ);
                } finally {
                    poses.popPose();
                }
            }
        } finally {
            DEFERRED.clear();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(WaterfallTestEngineBlockEntity engine) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }

    private record DeferredEngine(Level level, BlockPos position, double gameTime, float throttle,
                                  int variationSeed, WaterfallTestEngineVariant variant) {
    }
}
