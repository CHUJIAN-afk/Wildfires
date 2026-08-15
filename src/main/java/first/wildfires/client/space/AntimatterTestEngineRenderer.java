package first.wildfires.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.content.AntimatterTestEngineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;

/** Texture-free vacuum beam with outer blue-violet radiation and a narrow bright core. */
public final class AntimatterTestEngineRenderer implements BlockEntityRenderer<AntimatterTestEngineBlockEntity> {

    private static final int SEGMENTS = 12;
    private static final float FULL_BEAM_LENGTH = 48.0F;

    public AntimatterTestEngineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AntimatterTestEngineBlockEntity engine, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = engine.getLevel();
        if (level == null || level.dimension() != SpaceDimensions.ORBIT || engine.output() <= 0) {
            return;
        }

        float output = engine.output() / 100.0F;
        float time = level.getGameTime() + partialTick;
        float pulse = 0.92F + 0.08F * (float) Math.sin(time * 0.36F);
        float length = FULL_BEAM_LENGTH * output;
        poses.pushPose();
        // Station thrust is NORTH; the physical nozzle and radiation exhaust are SOUTH / +Z.
        poses.translate(0.5D, 0.5D, 1.001D);
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = poses.last();
        beam(consumer, pose, 0.38F * pulse * output, 0.92F * pulse * output, length,
                60, 100, 219, Math.round(52 * output), time * 0.07F);
        beam(consumer, pose, 0.16F * pulse * output, 0.30F * pulse * output, length * 0.98F,
                177, 50, 255, Math.round(168 * output), -time * 0.13F);
        beam(consumer, pose, 0.055F * output, 0.09F * output, length * 0.96F,
                238, 211, 255, Math.round(230 * output), time * 0.2F);
        poses.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(AntimatterTestEngineBlockEntity engine) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    private static void beam(VertexConsumer consumer, PoseStack.Pose pose, float sourceRadius,
                             float tipRadius, float length, int red, int green, int blue,
                             int alpha, float phase) {
        for (int segment = 0; segment < SEGMENTS; segment++) {
            float angle0 = phase + segment * ((float) (Math.PI * 2.0D) / SEGMENTS);
            float angle1 = phase + (segment + 1) * ((float) (Math.PI * 2.0D) / SEGMENTS);
            float x0 = (float) Math.cos(angle0);
            float y0 = (float) Math.sin(angle0);
            float x1 = (float) Math.cos(angle1);
            float y1 = (float) Math.sin(angle1);
            vertex(consumer, pose, x0 * sourceRadius, y0 * sourceRadius, 0.0F, red, green, blue, alpha);
            vertex(consumer, pose, x1 * sourceRadius, y1 * sourceRadius, 0.0F, red, green, blue, alpha);
            vertex(consumer, pose, x1 * tipRadius, y1 * tipRadius, length, red, green, blue, 0);
            vertex(consumer, pose, x0 * tipRadius, y0 * tipRadius, length, red, green, blue, 0);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                               int red, int green, int blue, int alpha) {
        consumer.vertex(pose.pose(), x, y, z).color(red, green, blue, alpha).endVertex();
    }
}
