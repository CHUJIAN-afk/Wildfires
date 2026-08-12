/*
 * Adapted from NTM: Space SkyProviderCelestial.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: ported fixed-function GL rendering to Forge 1.20.1 buffers and
 * RenderSystem state, and consumes Wildfires CelestialState-derived visual layers.
 */
package first.wildfires.client.space.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Forge 1.20.1 adaptation of NTM Space's star cubemap, flat sun/corona and distant point passes.
 * The four referenced textures are verbatim LGPL-3.0 resources recorded under third_party/ntm-space.
 */
public final class NtmOrbitSkyRenderer {

    public static final ResourceLocation NIGHT = texture("night.png");
    public static final ResourceLocation SUN = texture("kerbol.png");
    public static final ResourceLocation SUN_SPIKE = texture("sunspike.png");
    public static final ResourceLocation PLANET_POINT = texture("planet.png");

    /**
     * Exact NTM night.png cell-to-face mapping after SkyProviderCelestial's local rotations.
     * This atlas is not compatible with the Genesis planet cubemap order.
     */
    static final List<NightFace> NIGHT_FACES = List.of(
            face(4, -1, 1, 1, -1, -1, 1, -1, -1, -1, -1, 1, -1),
            face(1, 1, 1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1),
            face(0, -1, -1, 1, 1, -1, 1, 1, -1, -1, -1, -1, -1),
            face(5, -1, 1, -1, -1, -1, -1, 1, -1, -1, 1, 1, -1),
            face(2, 1, 1, -1, 1, -1, -1, 1, -1, 1, 1, 1, 1),
            face(3, 1, 1, 1, 1, -1, 1, -1, -1, 1, -1, 1, 1));

    private static VertexBuffer nightSkybox;
    private static VertexBuffer blackSkybox;

    private NtmOrbitSkyRenderer() {
    }

    public static void drawNight(PoseStack poseStack, Matrix4f projectionMatrix, double starVisibility) {
        ensure();
        // NTM orbit's WorldProvider returns exactly black sky/fog. The 1.20 renderer otherwise
        // leaves the clear/fog colour behind the additive atlas, producing the incorrect blue void.
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        poseStack.pushPose();
        poseStack.scale(106.0F, 106.0F, 106.0F);
        blackSkybox.bind();
        blackSkybox.drawWithShader(poseStack.last().pose(), projectionMatrix, RenderSystem.getShader());
        VertexBuffer.unbind();
        poseStack.popPose();

        enableAdditiveBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, NIGHT);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F,
                (float) Math.max(0.0D, Math.min(0.6D, starVisibility * 0.6D)));
        poseStack.pushPose();
        poseStack.scale(106.0F, 106.0F, 106.0F);
        nightSkybox.bind();
        nightSkybox.drawWithShader(poseStack.last().pose(), projectionMatrix, RenderSystem.getShader());
        VertexBuffer.unbind();
        poseStack.popPose();
    }

    public static void drawSun(OrbitVisualRules.SunLayer sun, PoseStack poseStack) {
        Vec3 direction = vector(sun.direction());
        // A single standard-alpha photosphere both covers baked stars and preserves the texture's
        // transparent edge. NTM's separate untextured black square only worked acceptably in its
        // old fixed-function path and creates black rims/stripes on modern drivers.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawBillboardAt(SUN, direction, sun.renderDistance(), sun.renderHalfSize(),
                1.0F, 1.0F, 1.0F, 1.0F, poseStack);
        enableAdditiveBlend();
        // NTM draws the large additive flare after the photosphere.
        drawBillboardAt(SUN_SPIKE, direction, sun.renderDistance() - 0.1D,
                sun.renderHalfSize() * 3.0D, 1.0F, 1.0F, 1.0F, 1.0F, poseStack);
    }

    public static void drawPoint(OrbitVisualRules.BodyLayer body, PoseStack poseStack) {
        if (!(body.pointAlpha() > 0.001D)) {
            return;
        }
        OrbitProceduralTexture.Rgba color = OrbitProceduralTexture.pointColor(body.body());
        double pointHalfSize = body.renderDistance() / 100.0D;
        enableAdditiveBlend();
        drawBillboardAt(PLANET_POINT, vector(body.direction()), body.renderDistance(), pointHalfSize,
                (float) color.red(), (float) color.green(), (float) color.blue(),
                (float) body.pointAlpha(), poseStack);
    }

    private static void drawBillboardAt(ResourceLocation texture, Vec3 direction, double distance,
                                        double halfSize, float red, float green, float blue,
                                        float alpha, PoseStack poseStack) {
        Vec3 reference = Math.abs(direction.y) < 0.94D
                ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 right = direction.cross(reference).normalize().scale(halfSize);
        Vec3 up = right.cross(direction).normalize().scale(halfSize);
        Vec3 center = direction.scale(distance);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShaderColor(red, green, blue, alpha);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f matrix = poseStack.last().pose();
        vertex(builder, matrix, center.subtract(right).subtract(up), 0.0F, 1.0F);
        vertex(builder, matrix, center.add(right).subtract(up), 1.0F, 1.0F);
        vertex(builder, matrix, center.add(right).add(up), 1.0F, 0.0F);
        vertex(builder, matrix, center.subtract(right).add(up), 0.0F, 0.0F);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 point, float u, float v) {
        builder.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .uv(u, v).endVertex();
    }

    private static void ensure() {
        RenderSystem.assertOnRenderThread();
        if (nightSkybox != null) {
            return;
        }
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (NightFace face : NIGHT_FACES) {
            skyboxVertex(builder, face.first(), face.uMin(), face.vMin());
            skyboxVertex(builder, face.second(), face.uMin(), face.vMax());
            skyboxVertex(builder, face.third(), face.uMax(), face.vMax());
            skyboxVertex(builder, face.fourth(), face.uMax(), face.vMin());
        }
        nightSkybox = new VertexBuffer(VertexBuffer.Usage.STATIC);
        nightSkybox.bind();
        nightSkybox.upload(builder.end());
        VertexBuffer.unbind();

        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (NightFace face : NIGHT_FACES) {
            blackSkyboxVertex(builder, face.first());
            blackSkyboxVertex(builder, face.second());
            blackSkyboxVertex(builder, face.third());
            blackSkyboxVertex(builder, face.fourth());
        }
        blackSkybox = new VertexBuffer(VertexBuffer.Usage.STATIC);
        blackSkybox.bind();
        blackSkybox.upload(builder.end());
        VertexBuffer.unbind();
    }

    private static void skyboxVertex(BufferBuilder builder, NightVertex vertex, double u, double v) {
        builder.vertex(vertex.x(), vertex.y(), vertex.z())
                .uv((float) u, (float) v).endVertex();
    }

    private static void blackSkyboxVertex(BufferBuilder builder, NightVertex vertex) {
        builder.vertex(vertex.x(), vertex.y(), vertex.z())
                .color(0, 0, 0, 255).endVertex();
    }

    private static NightFace face(int atlasIndex, int... coordinates) {
        if (coordinates.length != 12) {
            throw new IllegalArgumentException("NTM night face requires four XYZ vertices");
        }
        return new NightFace(atlasIndex,
                new NightVertex(coordinates[0], coordinates[1], coordinates[2]),
                new NightVertex(coordinates[3], coordinates[4], coordinates[5]),
                new NightVertex(coordinates[6], coordinates[7], coordinates[8]),
                new NightVertex(coordinates[9], coordinates[10], coordinates[11]));
    }

    private static void enableAdditiveBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
    }

    private static Vec3 vector(first.wildfires.api.celestial.CelestialVector vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static ResourceLocation texture(String file) {
        return ResourceLocation.fromNamespaceAndPath("wildfires",
                "textures/third_party/ntm_space/" + file);
    }

    public static void close() {
        RenderSystem.assertOnRenderThread();
        if (nightSkybox != null) {
            nightSkybox.close();
            nightSkybox = null;
        }
        if (blackSkybox != null) {
            blackSkybox.close();
            blackSkybox = null;
        }
        VertexBuffer.unbind();
    }

    record NightVertex(int x, int y, int z) {
    }

    record NightFace(int atlasIndex, NightVertex first, NightVertex second,
                     NightVertex third, NightVertex fourth) {

        double uMin() {
            return (atlasIndex % 3) / 3.0D;
        }

        double uMax() {
            return uMin() + 1.0D / 3.0D;
        }

        double vMin() {
            return (atlasIndex / 3) / 2.0D;
        }

        double vMax() {
            return vMin() + 0.5D;
        }
    }
}
