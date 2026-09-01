package first.wildfires.client.spell;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import first.wildfires.compats.irons_spellbooks.entity.GalaxyHymnHomingStar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Real movement-history ribbon; the cross-star body itself is a ParticleEngine GPU batch. */
public final class GalaxyHymnHomingStarRenderer extends EntityRenderer<GalaxyHymnHomingStar> {

    private static final int FULL_BRIGHT = 0x00F000F0;

    public GalaxyHymnHomingStarRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(GalaxyHymnHomingStar entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        Vec3 renderOrigin = new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
        renderTrajectoryRibbon(entity, renderOrigin, poseStack, buffers);

        super.render(entity, entityYaw, partialTick, poseStack, buffers, FULL_BRIGHT);
    }

    private static void renderTrajectoryRibbon(GalaxyHymnHomingStar entity, Vec3 renderOrigin,
                                               PoseStack poseStack, MultiBufferSource buffers) {
        List<Vec3> points = new ArrayList<>(entity.getClientTrailPositions());
        if (points.isEmpty() || points.get(points.size() - 1).distanceToSqr(renderOrigin) > 1.0E-6D) {
            points.add(renderOrigin);
        }
        if (points.size() < 2) {
            return;
        }
        Vec3 cameraPosition = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose pose = poseStack.last();
        for (int index = 0; index < points.size() - 1; index++) {
            Vec3 firstWorld = points.get(index);
            Vec3 secondWorld = points.get(index + 1);
            Vec3 direction = secondWorld.subtract(firstWorld);
            if (direction.lengthSqr() < 1.0E-7D) {
                continue;
            }
            Vec3 midpoint = firstWorld.add(secondWorld).scale(0.5D);
            Vec3 toCamera = cameraPosition.subtract(midpoint);
            Vec3 side = direction.cross(toCamera);
            if (side.lengthSqr() < 1.0E-7D) {
                side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
            }
            if (side.lengthSqr() < 1.0E-7D) {
                continue;
            }
            float progress0 = index / (float) (points.size() - 1);
            float progress1 = (index + 1) / (float) (points.size() - 1);
            double width0 = 0.015D + 0.13D * progress0;
            double width1 = 0.015D + 0.13D * progress1;
            Vec3 side0 = side.normalize().scale(width0);
            Vec3 side1 = side.normalize().scale(width1);
            Vec3 first = firstWorld.subtract(renderOrigin);
            Vec3 second = secondWorld.subtract(renderOrigin);
            ribbonVertex(consumer, pose, first.add(side0), 0.01F, 0.08F, 0.58F, progress0 * 0.72F);
            ribbonVertex(consumer, pose, first.subtract(side0), 0.02F, 0.16F, 0.86F, progress0 * 0.78F);
            ribbonVertex(consumer, pose, second.subtract(side1), 0.12F, 0.52F, 1.0F, progress1 * 0.92F);
            ribbonVertex(consumer, pose, second.add(side1), 0.30F, 0.76F, 1.0F, progress1);
        }
    }

    private static void ribbonVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point,
                                     float red, float green, float blue, float alpha) {
        consumer.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .color(red, green, blue, alpha).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(GalaxyHymnHomingStar entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
