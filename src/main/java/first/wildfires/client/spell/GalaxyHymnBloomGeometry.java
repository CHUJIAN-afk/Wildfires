package first.wildfires.client.spell;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

/** Camera-facing additive gradients that provide shader-backed bloom without an external pack. */
public final class GalaxyHymnBloomGeometry {

    private GalaxyHymnBloomGeometry() {
    }

    public static void render(PoseStack poseStack, VertexConsumer consumer, float radius,
                              float red, float green, float blue, float alpha) {
        addLayer(poseStack.last(), consumer, radius, red, green, blue, alpha * 0.42F);
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F));
        addLayer(poseStack.last(), consumer, radius * 0.72F,
                red * 0.72F, green * 0.88F, blue, alpha * 0.68F);
        poseStack.popPose();
    }

    /** Eight bounded additive scales, mirroring octave bloom reconstruction without a screen atlas. */
    public static void renderMultiScale(PoseStack poseStack, VertexConsumer consumer, float baseRadius,
                                        float red, float green, float blue, float alpha) {
        for (int octave = 0; octave < 8; octave++) {
            float radius = baseRadius * (float) Math.pow(2.0D, octave * 0.255D);
            float weight = alpha * 0.19F * (float) Math.pow(0.72D, octave);
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(octave * 11.25F));
            addLayer(poseStack.last(), consumer, radius, red, green, blue, weight);
            poseStack.popPose();
        }
    }

    /** Two camera-facing luminous axes form a stable four-point cross without a textured black quad. */
    public static void renderCrossStar(PoseStack poseStack, VertexConsumer consumer,
                                       float radius, float halfWidth,
                                       float red, float green, float blue, float alpha) {
        for (int axis = 0; axis < 2; axis++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(axis * 90.0F));
            float axisRadius = axis == 0 ? radius : radius * 0.78F;
            addRayPair(poseStack.last(), consumer, axisRadius, halfWidth,
                    red, green, blue, alpha * 0.48F);
            addRayPair(poseStack.last(), consumer, axisRadius * 0.68F, halfWidth * 0.42F,
                    Math.min(1.0F, red * 1.6F + 0.18F),
                    Math.min(1.0F, green * 1.25F + 0.18F), 1.0F, alpha * 0.92F);
            poseStack.popPose();
        }
        renderCore(poseStack, consumer, halfWidth * 1.65F,
                Math.min(1.0F, red * 1.8F + 0.30F),
                Math.min(1.0F, green * 1.35F + 0.25F), 1.0F, alpha);
    }

    private static void addRayPair(PoseStack.Pose pose, VertexConsumer consumer,
                                   float radius, float halfWidth,
                                   float red, float green, float blue, float alpha) {
        addTaperedRay(pose, consumer, radius, halfWidth, red, green, blue, alpha);
        addTaperedRay(pose, consumer, -radius, halfWidth, red, green, blue, alpha);
    }

    private static void addTaperedRay(PoseStack.Pose pose, VertexConsumer consumer,
                                      float length, float halfWidth,
                                      float red, float green, float blue, float alpha) {
        float tipWidth = Math.max(0.006F, halfWidth * 0.08F);
        float tipAlpha = alpha * 0.08F;
        vertex(consumer, pose, 0.0F, -halfWidth, red, green, blue, alpha);
        vertex(consumer, pose, length, -tipWidth, red, green, blue, tipAlpha);
        vertex(consumer, pose, length, tipWidth, red, green, blue, tipAlpha);
        vertex(consumer, pose, 0.0F, halfWidth, red, green, blue, alpha);
    }

    private static void renderCore(PoseStack poseStack, VertexConsumer consumer, float radius,
                                   float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        vertex(consumer, pose, 0.0F, -radius, red, green, blue, alpha);
        vertex(consumer, pose, radius, 0.0F, red, green, blue, alpha * 0.92F);
        vertex(consumer, pose, 0.0F, radius, red, green, blue, alpha);
        vertex(consumer, pose, -radius, 0.0F, red, green, blue, alpha * 0.92F);
    }

    private static void addLayer(PoseStack.Pose pose, VertexConsumer consumer, float radius,
                                 float red, float green, float blue, float alpha) {
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            float x = (quadrant == 0 || quadrant == 3) ? radius : -radius;
            float y = quadrant < 2 ? radius : -radius;
            vertex(consumer, pose, 0.0F, 0.0F, red, green, blue, alpha);
            vertex(consumer, pose, x, 0.0F, red, green, blue, 0.0F);
            vertex(consumer, pose, x, y, red, green, blue, 0.0F);
            vertex(consumer, pose, 0.0F, y, red, green, blue, 0.0F);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y,
                               float red, float green, float blue, float alpha) {
        consumer.vertex(pose.pose(), x, y, 0.0F).color(red, green, blue, alpha).endVertex();
    }
}
