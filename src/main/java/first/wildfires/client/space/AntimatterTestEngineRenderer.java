package first.wildfires.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.Wildfires;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.content.AntimatterTestEngineBlockEntity;
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

/** Positions the GLSL radiant-drive reconstruction on the fixed south-facing exhaust plane. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT)
public final class AntimatterTestEngineRenderer implements BlockEntityRenderer<AntimatterTestEngineBlockEntity> {

    private static final List<DeferredEngine> DEFERRED = new ArrayList<>();

    public AntimatterTestEngineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AntimatterTestEngineBlockEntity engine, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = engine.getLevel();
        if (level == null || level.dimension() != SpaceDimensions.ORBIT || engine.output() <= 0) {
            return;
        }

        DEFERRED.add(new DeferredEngine(level, engine.getBlockPos().immutable(),
                (double) level.getGameTime() + partialTick,
                engine.output() / 100.0F, engine.getBlockPos().hashCode()));
    }

    /** Draws after buffered entities so the already-populated depth buffer orders the additive plume. */
    @SubscribeEvent
    public static void renderDeferred(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
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
                    // Station thrust is NORTH; the physical nozzle and radiation exhaust are SOUTH / +Z.
                    poses.translate(engine.position.getX() + 0.5D - camera.x,
                            engine.position.getY() + 0.5D - camera.y,
                            engine.position.getZ() + 1.001D - camera.z);
                    AntimatterRadiantDriveShader.render(poses, engine.gameTime,
                            engine.throttle, engine.variationSeed);
                } finally {
                    poses.popPose();
                }
            }
        } finally {
            DEFERRED.clear();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(AntimatterTestEngineBlockEntity engine) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 320;
    }

    private record DeferredEngine(Level level, BlockPos position, double gameTime,
                                  float throttle, int variationSeed) {
    }

}
