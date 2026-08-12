/*
 * Adapted from VS: Genesis PlanetRenderer and PlanetAtmosphereRenderer.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
 * Licensed under the Apache License, Version 2.0.
 * Wildfires modifications: removed VS2/Lodestone/VantagePoint dependencies, retained the
 * Genesis 3x2 face contract, and converted the meshes to Forge-managed static VertexBuffers.
 */
package first.wildfires.thirdparty.genesisadapt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

/** Static cube geometry using the exact Genesis north/west/south/east/down/up atlas order. */
public final class GenesisPlanetMesh {

    private static VertexBuffer surface;
    private static VertexBuffer atmosphere;

    private GenesisPlanetMesh() {
    }

    public static void ensure() {
        RenderSystem.assertOnRenderThread();
        if (surface != null) {
            return;
        }
        surface = uploadSurface();
        atmosphere = uploadAtmosphere();
    }

    public static void drawSurface(PoseStack poseStack, Matrix4f projectionMatrix) {
        draw(surface, poseStack, projectionMatrix);
    }

    public static void drawAtmosphere(PoseStack poseStack, Matrix4f projectionMatrix) {
        draw(atmosphere, poseStack, projectionMatrix);
    }

    private static void draw(VertexBuffer buffer, PoseStack poseStack, Matrix4f projectionMatrix) {
        if (buffer == null || RenderSystem.getShader() == null) {
            return;
        }
        buffer.bind();
        buffer.drawWithShader(poseStack.last().pose(), projectionMatrix, RenderSystem.getShader());
        VertexBuffer.unbind();
    }

    private static VertexBuffer uploadSurface() {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        // Keep the exact Genesis winding and UV orientation.  A generic per-face winding looks
        // equivalent on paper but reverses several atlas cells once back-face culling is enabled.
        surfaceFace(builder, GenesisCubeAtlasLayout.face(2),
                -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1); // south +Z
        surfaceFace(builder, GenesisCubeAtlasLayout.face(0),
                1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1); // north -Z
        surfaceFace(builder, GenesisCubeAtlasLayout.face(1),
                -1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1); // west -X
        surfaceFace(builder, GenesisCubeAtlasLayout.face(3),
                1, -1, 1, 1, -1, -1, 1, 1, -1, 1, 1, 1); // east +X
        surfaceFace(builder, GenesisCubeAtlasLayout.face(4),
                -1, -1, -1, 1, -1, -1, 1, -1, 1, -1, -1, 1); // down -Y
        surfaceFace(builder, GenesisCubeAtlasLayout.face(5),
                -1, 1, 1, 1, 1, 1, 1, 1, -1, -1, 1, -1); // up +Y
        return upload(builder);
    }

    private static void surfaceFace(BufferBuilder builder, GenesisCubeAtlasLayout.Face face,
                                    int... coordinates) {
        if (coordinates.length != 12) {
            throw new IllegalArgumentException("Genesis surface face requires four XYZ corners");
        }
        surfaceVertex(builder, face, coordinates[0], coordinates[1], coordinates[2], 0.0D, 1.0D);
        surfaceVertex(builder, face, coordinates[3], coordinates[4], coordinates[5], 1.0D, 1.0D);
        surfaceVertex(builder, face, coordinates[6], coordinates[7], coordinates[8], 1.0D, 0.0D);
        surfaceVertex(builder, face, coordinates[9], coordinates[10], coordinates[11], 0.0D, 0.0D);
    }

    private static void surfaceVertex(BufferBuilder builder, GenesisCubeAtlasLayout.Face face,
                                      int x, int y, int z, double localU, double localV) {
        GenesisCubeAtlasLayout.Uv uv = GenesisCubeAtlasLayout.atlasUv(face, localU, localV);
        // Genesis uses the normalized cube corner in object space, then rotates that normal with
        // the planet.  This produces its continuous center-star terminator across cube seams.
        double inverseLength = 1.0D / Math.sqrt(x * x + y * y + z * z);
        builder.vertex(x, y, z)
                .uv((float) uv.u(), (float) uv.v())
                .color(255, 255, 255, 255)
                .normal((float) (x * inverseLength), (float) (y * inverseLength),
                        (float) (z * inverseLength))
                .endVertex();
    }

    private static VertexBuffer uploadAtmosphere() {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        atmosphereFace(builder, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1);
        atmosphereFace(builder, 1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1);
        atmosphereFace(builder, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1);
        atmosphereFace(builder, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1, 1, 1);
        atmosphereFace(builder, -1, -1, -1, 1, -1, -1, 1, -1, 1, -1, -1, 1);
        atmosphereFace(builder, -1, 1, 1, 1, 1, 1, 1, 1, -1, -1, 1, -1);
        return upload(builder);
    }

    private static void atmosphereFace(BufferBuilder builder, int... coordinates) {
        for (int index = 0; index < coordinates.length; index += 3) {
            atmosphereVertex(builder, coordinates[index], coordinates[index + 1], coordinates[index + 2]);
        }
    }

    private static void atmosphereVertex(BufferBuilder builder, int x, int y, int z) {
        int red = channel(x);
        int green = channel(y);
        int blue = channel(z);
        builder.vertex(x, y, z)
                .color(red, green, blue, 255).endVertex();
    }

    private static int channel(double coordinate) {
        return (int) Math.round((coordinate * 0.5D + 0.5D) * 255.0D);
    }

    private static VertexBuffer upload(BufferBuilder builder) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();
        return buffer;
    }

    public static void close() {
        RenderSystem.assertOnRenderThread();
        if (surface != null) {
            surface.close();
            surface = null;
        }
        if (atmosphere != null) {
            atmosphere.close();
            atmosphere = null;
        }
        VertexBuffer.unbind();
    }
}
