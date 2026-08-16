/*
 * Adapted from Waterfall's Additive Dynamic/Billboard Additive shaders and radiant-drive config.
 * Copyright Waterfall and KSP Interstellar Extended contributors.
 * SPDX-License-Identifier: CC-BY-NC-SA-4.0
 */
package first.wildfires.client.space;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import first.wildfires.Wildfires;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import org.slf4j.Logger;

/** Literal GLSL 150 port of Waterfall's Additive Dynamic and Billboard Additive materials. */
public final class AntimatterRadiantDriveShader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float FULL_LENGTH = 128.0F;
    private static final float KSP_TO_MC = FULL_LENGTH / 750.0F;
    private static final float OUTER_BRIGHTNESS_MULTIPLIER = 1.35F;
    private static final ResourceLocation NOISE_TEXTURE = Wildfires.rl("textures/effect/fx-noise-5.png");
    private static final ResourceLocation FLARE_TEXTURE = Wildfires.rl("textures/effect/fx_flarelamp-1.png");
    private static final ResourceLocation CYLINDER_MESH = Wildfires.rl("models/effect/fx-cylinder.mesh");
    private static final ResourceLocation BILLBOARD_MESH = Wildfires.rl("models/effect/fx-billboard-generic-1.mesh");
    private static DynamicBindings dynamic;
    private static FlareBindings flare;
    private static VertexBuffer beamGeometry;
    private static VertexBuffer flareGeometry;

    private AntimatterRadiantDriveShader() {
    }

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Wildfires.rl("antimatter_radiant_drive"),
                DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL), loaded -> dynamic = DynamicBindings.create(loaded));
        event.registerShader(new ShaderInstance(event.getResourceProvider(), Wildfires.rl("antimatter_radiant_flare"),
                DefaultVertexFormat.POSITION_TEX_COLOR), loaded -> flare = FlareBindings.create(loaded));
    }

    public static void render(PoseStack poses, double gameTime, float throttle, int variationSeed) {
        if (dynamic == null || flare == null || throttle <= 0.0F) {
            return;
        }
        ensureGeometry();
        if (beamGeometry == null || flareGeometry == null) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        float random = randomController(variationSeed, gameTime);
        renderBeam(poses, gameTime, throttle, random, true);
        renderBeam(poses, gameTime, throttle, random, false);
        renderFlare(poses, throttle, random);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void renderBeam(PoseStack poses, double gameTime, float throttle, float random, boolean outer) {
        DynamicBindings active = dynamic;
        RenderSystem.setShader(active);
        RenderSystem.setShaderTexture(0, outer ? NOISE_TEXTURE : FLARE_TEXTURE);
        active.time.set((float) (gameTime / 20.0D));
        setMaterial(active, throttle, random, outer);
        beamGeometry.bind();
        beamGeometry.drawWithShader(poses.last().pose(), RenderSystem.getProjectionMatrix(), active.shader);
        VertexBuffer.unbind();
    }

    private static void setMaterial(DynamicBindings uniforms, float throttle, float random, boolean outer) {
        float lengthScale = outer ? curve(throttle, 0.0F, 0.0F, 0.02F, 0.1F, 1.0F, 1.0F)
                : curve(throttle, 0.0F, 0.1F, 1.0F, 1.0F);
        float radialScale = outer ? curve(throttle, 0.0F, 0.8F, 1.0F, 1.0F)
                : curve(throttle, 0.0F, 0.5F, 1.0F, 1.0F);
        float brightness = outer ? curve(throttle, 0.0F, 0.2F, 0.5F, 1.0F, 1.0F, 1.0F)
                : curve(throttle, 0.0F, 0.0F, 0.05F, 0.5F, 0.2F, 0.7F, 1.0F, 1.0F);
        if (outer) brightness *= OUTER_BRIGHTNESS_MULTIPLIER;
        // The original cylinder's +Y axis is mapped to this engine's physical +Z exhaust.
        float centerXFluctuation = 0.99F + 0.02F * random;
        float centerZFluctuation = 0.9F + 0.2F * random;
        uniforms.modelScale.set(KSP_TO_MC * (outer ? 1.0F : 0.4F) * radialScale
                        * (outer ? 1.0F : centerXFluctuation),
                KSP_TO_MC * (outer ? 1.0F : 0.4F) * radialScale
                        * (outer ? 1.0F : centerZFluctuation),
                FULL_LENGTH * lengthScale);
        uniforms.startTint.set(outer ? 0.697851002F : 1.0F, outer ? 0.07226865F : 1.0F,
                1.0F, 1.0F);
        uniforms.endTint.set(outer ? 0.236468881F : 0.70443958F, outer ? 0.392459095F : 0.195418835F,
                outer ? 0.860429764F : 1.0F, 1.0F);
        uniforms.tintFalloff.set(outer ? 4.42360401F : 3.00305843F);
        uniforms.falloff.set(outer ? 5.35888052F : 2.22444081F);
        uniforms.fresnel.set(outer ? 5.45999146F : 6.57221222F);
        uniforms.fresnelInvert.set(0.0F);
        uniforms.noise.set(outer ? 0.606665671F : 0.0F);
        uniforms.brightness.set(brightness);
        uniforms.fadeIn.set(0.0F);
        uniforms.fadeOut.set(outer ? 0.0F : 0.106166504F);
        uniforms.expandOffset.set(0.0F);
        // The orbit-only atmosphere controller resolves to its vacuum endpoints.
        uniforms.expandLinear.set(outer ? 2.0F : 1.01110959F);
        uniforms.expandSquare.set(outer ? 10.0F : 0.0F);
        uniforms.expandBounded.set(outer ? 1.0F : curve(throttle, 0.0F, 0.0F, 0.3F, 0.0F, 1.0F, 1.0F));
        uniforms.falloffStart.set(0.0F);
        uniforms.symmetry.set(0.0F);
        uniforms.symmetryStrength.set(1.0F);
        uniforms.speedX.set(outer ? 5.05554771F : 0.0F);
        uniforms.speedY.set(outer ? 86.944313F : 58.6332474F);
        uniforms.seed.set(1.0F);
        uniforms.tileX.set(1.0F);
        uniforms.tileY.set(1.0F);
        uniforms.clipBrightness.set(50.0F);
        uniforms.plumeDirection.set(0.0F, 0.0F, -1.0F);
    }

    private static void renderFlare(PoseStack poses, float throttle, float random) {
        float scale = curve(throttle, 0.0F, 0.0F, 0.02F, 0.2F, 1.0F, 1.0F);
        FlareBindings active = flare;
        RenderSystem.setShader(active);
        RenderSystem.setShaderTexture(0, FLARE_TEXTURE);
        active.startTint.set(0.686591923F, 0.553144574F, 1.0F, 1.0F);
        float fluctuation = 0.9F + 0.2F * random;
        active.scale.set(10.0F * KSP_TO_MC * scale * fluctuation,
                10.0F * KSP_TO_MC * scale * fluctuation);
        poses.pushPose();
        // Waterfall Flare POSITIONMODIFIER: local zCurve = 0..0.2 over throttle.
        poses.translate(0.0D, 0.0D, 0.2D * KSP_TO_MC * throttle);
        flareGeometry.bind();
        flareGeometry.drawWithShader(poses.last().pose(), RenderSystem.getProjectionMatrix(), active.shader);
        VertexBuffer.unbind();
        poses.popPose();
    }

    private static float curve(float value, float... knots) {
        for (int index = 0; index < knots.length - 2; index += 2) {
            float x0 = knots[index];
            float x1 = knots[index + 2];
            if (value <= x1) {
                float fraction = Math.max(0.0F, Math.min(1.0F, (value - x0) / (x1 - x0)));
                // Every key in the source config has zero in/out tangents. Unity's FloatCurve
                // therefore evaluates each interval as cubic Hermite smoothstep.
                fraction = fraction * fraction * (3.0F - 2.0F * fraction);
                return knots[index + 1] + (knots[index + 3] - knots[index + 1]) * fraction;
            }
        }
        return knots[knots.length - 1];
    }

    private static float randomController(int seed, double gameTime) {
        // Waterfall RandomnessController uses Random.Range(-1, 1) on every active update.
        long mixed = Double.doubleToRawLongBits(gameTime) ^ ((long) seed * 0x9E3779B97F4A7C15L);
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return ((mixed >>> 40) / 8388607.5F) - 1.0F;
    }

    private static void ensureGeometry() {
        RenderSystem.assertOnRenderThread();
        if (beamGeometry == null) {
            beamGeometry = loadMesh(CYLINDER_MESH, true);
        }
        if (flareGeometry == null) {
            flareGeometry = loadMesh(BILLBOARD_MESH, false);
        }
    }

    private static VertexBuffer loadMesh(ResourceLocation id, boolean normals) {
        try (InputStream raw = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow().open();
             DataInputStream input = new DataInputStream(raw)) {
            int vertexCount = input.readInt();
            float[] vertices = new float[vertexCount * 8];
            for (int index = 0; index < vertices.length; index++) {
                vertices[index] = input.readFloat();
            }
            int indexCount = input.readInt();
            int[] indices = new int[indexCount];
            for (int index = 0; index < indexCount; index++) {
                indices[index] = input.readInt();
            }
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.TRIANGLES, normals
                    ? DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL : DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int index : indices) {
                int offset = index * 8;
                if (normals) {
                    builder.vertex(vertices[offset], vertices[offset + 1], vertices[offset + 2])
                            .uv(vertices[offset + 3], vertices[offset + 4]).color(255, 255, 255, 255)
                            .normal(vertices[offset + 5], vertices[offset + 6], vertices[offset + 7]).endVertex();
                } else {
                    builder.vertex(vertices[offset], vertices[offset + 1], vertices[offset + 2])
                            .uv(vertices[offset + 3], vertices[offset + 4]).color(255, 255, 255, 255).endVertex();
                }
            }
            return upload(builder);
        } catch (IOException exception) {
            LOGGER.error("Unable to load Waterfall mesh {}", id, exception);
            return null;
        }
    }

    private static VertexBuffer upload(BufferBuilder builder) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();
        return buffer;
    }

    public static void reset() {
        if (beamGeometry != null) {
            beamGeometry.close();
            beamGeometry = null;
        }
        if (flareGeometry != null) {
            flareGeometry.close();
            flareGeometry = null;
        }
    }

    private record DynamicBindings(ShaderInstance shader, AbstractUniform time, AbstractUniform modelScale,
                                   AbstractUniform startTint, AbstractUniform endTint, AbstractUniform tintFalloff,
                                   AbstractUniform falloff, AbstractUniform fresnel, AbstractUniform fresnelInvert,
                                   AbstractUniform noise, AbstractUniform brightness, AbstractUniform fadeIn,
                                   AbstractUniform fadeOut, AbstractUniform expandOffset, AbstractUniform expandLinear,
                                   AbstractUniform expandSquare, AbstractUniform expandBounded,
                                   AbstractUniform falloffStart, AbstractUniform symmetry,
                                   AbstractUniform symmetryStrength, AbstractUniform seed, AbstractUniform speedX, AbstractUniform speedY,
                                   AbstractUniform tileX, AbstractUniform tileY, AbstractUniform clipBrightness,
                                   AbstractUniform plumeDirection) implements Supplier<ShaderInstance> {
        private static DynamicBindings create(ShaderInstance shader) {
            return new DynamicBindings(shader, shader.safeGetUniform("Time"), shader.safeGetUniform("ModelScale"),
                    shader.safeGetUniform("StartTint"), shader.safeGetUniform("EndTint"),
                    shader.safeGetUniform("TintFalloff"), shader.safeGetUniform("Falloff"),
                    shader.safeGetUniform("Fresnel"), shader.safeGetUniform("FresnelInvert"),
                    shader.safeGetUniform("Noise"), shader.safeGetUniform("Brightness"), shader.safeGetUniform("FadeIn"),
                    shader.safeGetUniform("FadeOut"), shader.safeGetUniform("ExpandOffset"),
                    shader.safeGetUniform("ExpandLinear"), shader.safeGetUniform("ExpandSquare"),
                    shader.safeGetUniform("ExpandBounded"), shader.safeGetUniform("FalloffStart"),
                    shader.safeGetUniform("Symmetry"), shader.safeGetUniform("SymmetryStrength"), shader.safeGetUniform("Seed"),
                    shader.safeGetUniform("SpeedX"), shader.safeGetUniform("SpeedY"), shader.safeGetUniform("TileX"),
                    shader.safeGetUniform("TileY"), shader.safeGetUniform("ClipBrightness"),
                    shader.safeGetUniform("PlumeDirection"));
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }

    private record FlareBindings(ShaderInstance shader, AbstractUniform startTint, AbstractUniform scale)
            implements Supplier<ShaderInstance> {
        private static FlareBindings create(ShaderInstance shader) {
            return new FlareBindings(shader, shader.safeGetUniform("StartTint"), shader.safeGetUniform("Scale"));
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }
}
