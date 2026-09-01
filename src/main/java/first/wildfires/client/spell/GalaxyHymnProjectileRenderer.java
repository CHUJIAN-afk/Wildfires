package first.wildfires.client.spell;

/*
 * Uses the animated cosmic sprite redistributed from ArcaneVortex 0.6.8 under
 * the user's project-specific visual authorization. Wildfires replaces the
 * source tesseract with a three-dimensional blue column-cross star.
 * Evidence: third_party/arcanevortex/0.6.8/PROVENANCE.md
 */
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import first.wildfires.Wildfires;
import first.wildfires.compats.irons_spellbooks.entity.GalaxyHymnCoreProjectile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Core-only renderer: animated center plus a rotating three-axis column cross. */
public final class GalaxyHymnProjectileRenderer extends EntityRenderer<GalaxyHymnCoreProjectile> {

    private static final ResourceLocation TEXTURE = Wildfires.rl("textures/particle/star/cosmic_0.png");
    private static final int FULL_BRIGHT = 0x00F000F0;

    public GalaxyHymnProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public void render(GalaxyHymnCoreProjectile entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        VertexConsumer rays = buffers.getBuffer(RenderType.lightning());
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 3.2F));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 2.1F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 4.6F));
        addAxisPrism(poseStack, rays, 0, 0.92F, 0.055F, 0.12F, 0.48F, 1.0F, 0.88F);
        addAxisPrism(poseStack, rays, 1, 0.92F, 0.055F, 0.08F, 0.34F, 1.0F, 0.84F);
        addAxisPrism(poseStack, rays, 2, 1.18F, 0.075F, 0.18F, 0.62F, 1.0F, 0.96F);
        poseStack.popPose();

        int animationTick = Math.floorMod(entity.tickCount, 10);
        int frame = animationTick < 7 ? 0 : animationTick - 6;
        float minimumV = frame * 0.25F;
        float maximumV = minimumV + 0.25F;
        VertexConsumer core = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        poseStack.pushPose();
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        GalaxyHymnBloomGeometry.render(poseStack, buffers.getBuffer(RenderType.lightning()),
                1.55F, 0.08F, 0.42F, 1.0F, 0.82F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 4.0F));
        renderTexturedQuad(poseStack, core, 0.40F, minimumV, maximumV, 220, 242, 255, 255);
        poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F));
        renderTexturedQuad(poseStack, core, 0.66F, minimumV, maximumV, 42, 120, 255, 150);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, FULL_BRIGHT);
    }

    private static void addAxisPrism(PoseStack poseStack, VertexConsumer consumer, int axis,
                                     float halfLength, float halfWidth,
                                     float red, float green, float blue, float alpha) {
        float[][] points = new float[8][3];
        for (int index = 0; index < 8; index++) {
            float along = (index & 1) == 0 ? -halfLength : halfLength;
            float first = (index & 2) == 0 ? -halfWidth : halfWidth;
            float second = (index & 4) == 0 ? -halfWidth : halfWidth;
            if (axis == 0) {
                points[index] = new float[]{along, first, second};
            } else if (axis == 1) {
                points[index] = new float[]{first, along, second};
            } else {
                points[index] = new float[]{first, second, along};
            }
        }
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1},
                {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        PoseStack.Pose pose = poseStack.last();
        for (int[] face : faces) {
            for (int point : face) {
                float[] value = points[point];
                colorVertex(consumer, pose, value[0], value[1], value[2], red, green, blue, alpha);
            }
        }
    }

    private static void renderTexturedQuad(PoseStack poseStack, VertexConsumer consumer, float halfSize,
                                           float minimumV, float maximumV,
                                           int red, int green, int blue, int alpha) {
        PoseStack.Pose pose = poseStack.last();
        texturedVertex(consumer, pose, -halfSize, -halfSize, 1.0F, maximumV, red, green, blue, alpha);
        texturedVertex(consumer, pose, halfSize, -halfSize, 0.0F, maximumV, red, green, blue, alpha);
        texturedVertex(consumer, pose, halfSize, halfSize, 0.0F, minimumV, red, green, blue, alpha);
        texturedVertex(consumer, pose, -halfSize, halfSize, 1.0F, minimumV, red, green, blue, alpha);
    }

    private static void texturedVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                       float x, float y, float u, float v,
                                       int red, int green, int blue, int alpha) {
        consumer.vertex(pose.pose(), x, y, 0.0F).color(red, green, blue, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT)
                .normal(pose.normal(), 0.0F, 0.0F, 1.0F).endVertex();
    }

    private static void colorVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                    float x, float y, float z,
                                    float red, float green, float blue, float alpha) {
        consumer.vertex(pose.pose(), x, y, z).color(red, green, blue, alpha).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(GalaxyHymnCoreProjectile entity) {
        return TEXTURE;
    }
}
