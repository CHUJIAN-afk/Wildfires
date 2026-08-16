/*
 * Adapted from Waterfall Additive Dynamic/Billboard Additive Directional shaders and the KSPIE
 * Daedalus v1 and v2 engine configurations.
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
import first.wildfires.space.content.WaterfallTestEngineVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Literal material/controller translation for the Daedalus v1 and v2 test engines. */
public final class WaterfallTranslatedEngineShader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float DAEDALUS_RADIAL_UNIT = 2.5F / 6.0F;
    private static final float DAEDALUS_LENGTH_UNIT = 128.0F / 658.0F;
    private static final float DAEDALUS_PLUME_BRIGHTNESS_BOOST = 2.5F;
    private static final float DAEDALUS_SOURCE_ORIGIN = 2.0F;
    private static final float DAEDALUS_V1_UNIT = 2.5F / 8.0F;
    private static final float DAEDALUS_V1_SOURCE_ORIGIN = -1.0F;
    private static final ResourceLocation CYLINDER_MESH =
            Wildfires.rl("models/effect/fx-cylinder.mesh");
    private static final ResourceLocation BILLBOARD_MESH =
            Wildfires.rl("models/effect/fx-billboard-generic-1.mesh");

    private static final Curve ZERO_TO_ONE = curve(key(0, 0, 0, 0), key(1, 1, 0, 0));
    private static final Curve ZERO_TO_POINT_TWO = curve(key(0, 0, 0, 0), key(1, 0.2F, 0, 0));
    private static final Curve ZERO_TO_POINT_THREE = curve(key(0, 0, 0, 0), key(1, 0.3F, 0, 0));
    private static final Curve ZERO_TO_POINT_SIX = curve(key(0, 0, 0, 0), key(1, 0.6F, 0, 0));
    private static final Curve DAEDALUS_LENGTH = curve(key(0, 0, 0, 0),
            key(0.1F, 0.5F, 0, 1), key(1, 1, 0, 0));

    private static final DynamicMaterial DAEDALUS_DIFFUSE = material("fx-noise-4.png")
            .tint(0.478431374F, 0.788235307F, 0.992156863F,
                    0.0196078438F, 0.223529413F, 0.972549021F)
            .tintFalloff(1.56721985F).falloff(10.0F).fresnel(2.93221784F)
            .noise(1.97166359F).fade(0.161777526F, 0.192110822F)
            .expand(0.0F, 3.53888321F, -0.909998536F, -2.42666268F)
            .randomizedSeed();
    private static final DynamicMaterial DAEDALUS_CORE = material("fx-ion-noise.png")
            .tint(0.0666666701F, 0.443137258F, 0.980392158F,
                    0.521568656F, 0.0117647061F, 0.980392158F)
            .tintFalloff(1.36499786F).falloff(8.99887466F).fresnel(6.16776848F)
            .noise(1.06166506F).expand(0.0F, 0.0F, -0.909998596F, 0.0F)
            .randomizedSeed();
    private static final DynamicMaterial DAEDALUS_TAIL = material("fx-ion-noise.png")
            .tint(0.0666666701F, 0.443137258F, 0.980392158F,
                    0.521568656F, 0.0117647061F, 0.980392158F)
            .tintFalloff(1.36499786F).falloff(8.99887466F).fresnel(6.16776848F)
            .noise(1.06166506F).fade(0.495443672F, 0.283110678F)
            .expand(0.0F, 10.0F, 0.707776666F, -1.41555345F)
            .randomizedSeed();

    private static final DynamicLayer[] DAEDALUS_LAYERS = {
            layer(6.0F, 130.0F, 2.0F, DAEDALUS_DIFFUSE, ZERO_TO_ONE, DAEDALUS_LENGTH),
            layer(1.29999995F, 300.0F, 2.0F, DAEDALUS_CORE, ZERO_TO_POINT_THREE,
                    DAEDALUS_LENGTH),
            layer(1.5F, 300.0F, 2.0F, DAEDALUS_CORE, ZERO_TO_POINT_TWO, DAEDALUS_LENGTH),
            layer(1.70000005F, 300.0F, 2.0F, DAEDALUS_CORE, ZERO_TO_POINT_TWO,
                    DAEDALUS_LENGTH),
            layer(2.0F, 300.0F, 2.0F, DAEDALUS_CORE, ZERO_TO_POINT_TWO, DAEDALUS_LENGTH),
            layer(2.20000005F, 300.0F, 2.0F, DAEDALUS_CORE, ZERO_TO_POINT_TWO,
                    DAEDALUS_LENGTH),
            layer(2.4000001F, 300.0F, 2.0F, DAEDALUS_CORE, ZERO_TO_POINT_TWO,
                    DAEDALUS_LENGTH),
            layer(2.5999999F, 300.0F, 2.0F, DAEDALUS_CORE, ZERO_TO_POINT_TWO,
                    DAEDALUS_LENGTH),
            layer(2.79999995F, 300.0F, 2.0F, DAEDALUS_CORE, ZERO_TO_POINT_TWO,
                    DAEDALUS_LENGTH),
            layer(3.0F, 300.0F, 2.0F, DAEDALUS_CORE, ZERO_TO_POINT_TWO, DAEDALUS_LENGTH),
            layer(2.20000005F, 600.0F, 60.0F, DAEDALUS_TAIL,
                    ZERO_TO_POINT_SIX, DAEDALUS_LENGTH)
    };

    private static final BillboardLayer[] DAEDALUS_BILLBOARDS = {
            billboard("fx_flarelens01.png", 30.0F, 40.0F, 8.0F,
                    1.0F, 1.0F, 1.0F, 0.0F,
                    curve(key(0, 0, 0, 0), key(0.05F, 0.5F, 0, 0),
                            key(1, 2.5F, 0, 0)),
                    curve(key(0, 0.9F, 0, 0), key(1, 1.1F, 0, 0))),
            billboard("fx_flareglow-1.png", 20.0F, 20.0F, 1.0F,
                    0.784313738F, 0.988235295F, 0.97647059F, 1.0F,
                    ZERO_TO_ONE, null)
    };

    private static final DynamicMaterial DAEDALUS_V1_DETONATION = material("fx-noise-4.png")
            .tint(0.627451003F, 0.952941179F, 0.905882359F,
                    0.0313725509F, 0.356862754F, 0.941176474F)
            .tintFalloff(1.18805373F).falloff(3.18499494F).fresnel(3.53888345F)
            .noise(3.26082826F).fade(0.176944166F, 0.671389401F)
            .expand(0.0F, -1.41555333F, 0.0F, 0.808887661F)
            .speed(0.0F, 83.9109879F).tile(1.63194346F, 1.25277734F)
            .randomizedSeed();
    private static final DynamicMaterial DAEDALUS_V1_OUTER_LONG = expansionMaterial(
            2.02221918F, 130.0F, -40.0F, 200.0F);
    private static final DynamicMaterial DAEDALUS_V1_EXPANSION = expansionMaterial(
            2.02221918F, 100.0F, -25.0F, 200.0F);
    private static final DynamicMaterial DAEDALUS_V1_INNER_OFFSET = expansionMaterial(
            3.33666158F, 100.0F, -25.0F, 83.9109879F);

    private static final DynamicLayer[] DAEDALUS_V1_LAYERS = {
            layer(2.5F, 20.0F, -1.20000005F, DAEDALUS_V1_DETONATION, ZERO_TO_ONE, null),
            layer(2.5F, 1070.0F, -1.0F, DAEDALUS_V1_OUTER_LONG, ZERO_TO_ONE, null),
            layer(1.5F, 670.0F, -1.0F, DAEDALUS_V1_EXPANSION, ZERO_TO_ONE, null),
            layer(1.0F, 670.0F, -1.0F, DAEDALUS_V1_EXPANSION, ZERO_TO_ONE, null),
            layer(3.0F, 20.0F, -1.20000005F, DAEDALUS_V1_DETONATION, ZERO_TO_ONE, null),
            layer(4.0F, 20.0F, -1.20000005F, DAEDALUS_V1_DETONATION, ZERO_TO_ONE, null),
            layer(1.70000005F, 670.0F, -1.0F, DAEDALUS_V1_EXPANSION, ZERO_TO_ONE, null),
            layer(0.800000012F, 670.0F, -1.0F, DAEDALUS_V1_EXPANSION, ZERO_TO_ONE, null),
            layer(1.10000002F, 670.0F, -1.0F, DAEDALUS_V1_INNER_OFFSET, ZERO_TO_ONE, null)
    };

    private static final BillboardLayer[] DAEDALUS_V1_BILLBOARDS = {
            billboard("fx_flarelens01.png", 30.0F, 30.0F, 1.0F,
                    1.0F, 1.0F, 1.0F, 0.0F,
                    curve(key(0, 0, 0, 0), key(1, 1.6F, 0, 0)), null),
            billboard("fx_flareglow-1.png", 20.0F, 20.0F, 1.0F,
                    0.105882354F, 0.0313725509F, 0.933333337F, 0.0F,
                    curve(key(0, 0, 0, 0), key(1, 1.6F, 0, 0)), null),
            billboard("fx_flarelamp-1.png", 20.0F, 20.0F, 1.0F,
                    0.345098048F, 0.631372571F, 0.972549021F, 0.0F,
                    curve(key(0, 0, 0, 0), key(1, 1.6F, 0, 0)), null),
            billboard("fx_flarelens02.png", 30.0F, 30.0F, 1.0F,
                    0.345098048F, 0.631372571F, 0.972549021F, 0.0F,
                    curve(key(0, 0, 0, 0), key(1, 2.0F, 0, 0)), null)
    };

    private static DynamicBindings dynamic;
    private static DirectionalBindings directional;
    private static VertexBuffer beamGeometry;
    private static VertexBuffer billboardGeometry;
    private static final Set<ResourceLocation> FILTERED_TEXTURES = new HashSet<>();

    private WaterfallTranslatedEngineShader() {
    }

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        Wildfires.rl("waterfall_test_engine_dynamic"),
                        DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),
                loaded -> dynamic = DynamicBindings.create(loaded));
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        Wildfires.rl("waterfall_billboard_directional"),
                        DefaultVertexFormat.POSITION_TEX_COLOR),
                loaded -> directional = DirectionalBindings.create(loaded));
    }

    public static void render(PoseStack poses, WaterfallTestEngineVariant variant,
                              double gameTime, float throttle, int variationSeed,
                              float cameraX, float cameraY, float cameraZ) {
        if (dynamic == null || directional == null || throttle <= 0.0F) return;
        ensureGeometry();
        if (beamGeometry == null || billboardGeometry == null) return;

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        boolean v1 = variant == WaterfallTestEngineVariant.DAEDALUS_V1;
        DynamicLayer[] layers = v1 ? DAEDALUS_V1_LAYERS : DAEDALUS_LAYERS;
        BillboardLayer[] billboards = v1 ? DAEDALUS_V1_BILLBOARDS : DAEDALUS_BILLBOARDS;
        float radialUnit = v1 ? DAEDALUS_V1_UNIT : DAEDALUS_RADIAL_UNIT;
        float lengthUnit = v1 ? DAEDALUS_V1_UNIT : DAEDALUS_LENGTH_UNIT;
        float sourceOrigin = v1 ? DAEDALUS_V1_SOURCE_ORIGIN : DAEDALUS_SOURCE_ORIGIN;
        float brightnessScale = v1 ? 1.0F : DAEDALUS_PLUME_BRIGHTNESS_BOOST;
        float random = randomController(variationSeed, gameTime);
        renderBillboards(poses, throttle, random, billboards,
                radialUnit, lengthUnit, sourceOrigin,
                cameraX, cameraY, cameraZ);
        renderDynamicLayers(poses, gameTime, throttle, variationSeed, layers,
                radialUnit, lengthUnit, sourceOrigin, brightnessScale);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void renderDynamicLayers(PoseStack poses, double gameTime, float throttle,
                                             int variationSeed, DynamicLayer[] layers,
                                             float radialUnit, float lengthUnit,
                                             float sourceOrigin, float plumeBrightnessScale) {
        for (int index = 0; index < layers.length; index++) {
            DynamicLayer layer = layers[index];
            DynamicMaterial material = layer.material;
            DynamicBindings active = dynamic;
            RenderSystem.setShader(active);
            RenderSystem.setShaderTexture(0, material.texture);
            useUnityFiltering(material.texture);
            active.time.set((float) (gameTime / 400.0D));
            float lengthThrottle = layer.lengthCurve == null ? 1.0F
                    : layer.lengthCurve.evaluate(throttle);
            active.modelScale.set(layer.radialScale * radialUnit,
                    layer.radialScale * radialUnit,
                    layer.sourceLength * lengthUnit * lengthThrottle);
            active.startTint.set(material.startR, material.startG, material.startB, 1.0F);
            active.endTint.set(material.endR, material.endG, material.endB, 1.0F);
            active.tintFalloff.set(material.tintFalloff);
            active.falloff.set(material.falloff);
            active.fresnel.set(material.fresnel);
            active.fresnelInvert.set(material.fresnelInvert);
            active.noise.set(material.noise);
            active.brightness.set(layer.brightnessCurve.evaluate(throttle) * plumeBrightnessScale);
            active.fadeIn.set(material.fadeIn);
            active.fadeOut.set(material.fadeOut);
            active.expandOffset.set(material.expandOffset);
            active.expandLinear.set(material.expandLinear);
            active.expandSquare.set(material.expandSquare);
            active.expandBounded.set(material.expandBounded);
            active.falloffStart.set(material.falloffStart);
            active.symmetry.set(material.symmetry);
            active.symmetryStrength.set(material.symmetryStrength);
            active.speedX.set(material.speedX);
            active.speedY.set(material.speedY);
            active.seed.set(material.randomizedSeed
                    ? materialSeed(variationSeed, index) : material.seed);
            active.tileX.set(material.tileX);
            active.tileY.set(material.tileY);
            active.clipBrightness.set(50.0F);
            active.plumeDirection.set(0.0F, 0.0F, -1.0F);

            poses.pushPose();
            poses.translate(0.0D, 0.0D,
                    (layer.sourcePosition - sourceOrigin) * lengthUnit);
            beamGeometry.bind();
            beamGeometry.drawWithShader(poses.last().pose(), RenderSystem.getProjectionMatrix(),
                    active.shader);
            VertexBuffer.unbind();
            poses.popPose();
        }
    }

    private static void renderBillboards(PoseStack poses, float throttle, float random,
                                         BillboardLayer[] layers, float radialUnit,
                                         float lengthUnit, float sourceOrigin,
                                         float cameraX, float cameraY, float cameraZ) {
        for (BillboardLayer layer : layers) {
            float brightness = layer.brightnessCurve.evaluate(throttle);
            if (layer.randomCurve != null) brightness *= layer.randomCurve.evaluate(random);
            DirectionalBindings active = directional;
            RenderSystem.setShader(active);
            RenderSystem.setShaderTexture(0, layer.texture);
            useUnityFiltering(layer.texture);
            active.startTint.set(layer.red, layer.green, layer.blue, 1.0F);
            active.scale.set(layer.sourceScaleX * radialUnit, layer.sourceScaleY * radialUnit,
                    layer.sourceScaleZ * radialUnit);
            active.cameraPosition.set(cameraX, cameraY, cameraZ);
            active.direction.set(0.0F, 0.0F, 1.0F);
            active.directionScale.set(layer.directionScale);
            active.brightness.set(brightness);
            poses.pushPose();
            poses.translate(0.0D, 0.0D, (layer.sourcePosition - sourceOrigin) * lengthUnit);
            billboardGeometry.bind();
            billboardGeometry.drawWithShader(poses.last().pose(), RenderSystem.getProjectionMatrix(),
                    active.shader);
            VertexBuffer.unbind();
            poses.popPose();
        }
    }

    private static void ensureGeometry() {
        RenderSystem.assertOnRenderThread();
        if (beamGeometry == null) beamGeometry = loadMesh(CYLINDER_MESH, true);
        if (billboardGeometry == null) billboardGeometry = loadMesh(BILLBOARD_MESH, false);
    }

    private static void useUnityFiltering(ResourceLocation texture) {
        if (FILTERED_TEXTURES.add(texture)) {
            Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
        }
    }

    private static VertexBuffer loadMesh(ResourceLocation id, boolean normals) {
        try (InputStream raw = Minecraft.getInstance().getResourceManager().getResource(id)
                .orElseThrow().open(); DataInputStream input = new DataInputStream(raw)) {
            int vertexCount = input.readInt();
            float[] vertices = new float[vertexCount * 8];
            for (int index = 0; index < vertices.length; index++) vertices[index] = input.readFloat();
            int indexCount = input.readInt();
            int[] indices = new int[indexCount];
            for (int index = 0; index < indexCount; index++) indices[index] = input.readInt();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.TRIANGLES, normals
                    ? DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL
                    : DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int index : indices) {
                int offset = index * 8;
                if (normals) {
                    builder.vertex(vertices[offset], vertices[offset + 1], vertices[offset + 2])
                            .uv(vertices[offset + 3], vertices[offset + 4])
                            .color(255, 255, 255, 255)
                            .normal(vertices[offset + 5], vertices[offset + 6],
                                    vertices[offset + 7]).endVertex();
                } else {
                    builder.vertex(vertices[offset], vertices[offset + 1], vertices[offset + 2])
                            .uv(vertices[offset + 3], vertices[offset + 4])
                            .color(255, 255, 255, 255).endVertex();
                }
            }
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(builder.end());
            VertexBuffer.unbind();
            return buffer;
        } catch (IOException exception) {
            LOGGER.error("Unable to load translated Waterfall mesh {}", id, exception);
            return null;
        }
    }

    public static void reset() {
        FILTERED_TEXTURES.clear();
        if (beamGeometry != null) {
            beamGeometry.close();
            beamGeometry = null;
        }
        if (billboardGeometry != null) {
            billboardGeometry.close();
            billboardGeometry = null;
        }
    }

    private static float randomController(int seed, double gameTime) {
        return mixedUnit(Double.doubleToRawLongBits(gameTime)
                ^ ((long) seed * 0x9E3779B97F4A7C15L));
    }

    private static float materialSeed(int seed, int layer) {
        return mixedUnit(((long) seed << 32) ^ (layer * 0x9E3779B97F4A7C15L));
    }

    private static float mixedUnit(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value >>> 40) / 8388607.5F) - 1.0F;
    }

    private static DynamicMaterial material(String texture) {
        return new DynamicMaterial(Wildfires.rl("textures/effect/" + texture));
    }

    private static DynamicMaterial expansionMaterial(float offset, float linear, float bounded,
                                                      float speedY) {
        return material("fx-noise-4.png")
                .tint(0.101960786F, 0.188235298F, 0.501960814F,
                        0.266666681F, 0.0117647061F, 0.305882365F)
                .tintFalloff(1.18805373F).falloff(3.52889895F).fresnel(10.0F)
                .noise(3.26082826F).fade(0.00455631875F, 0.671389401F)
                .expand(offset, linear, -0.707776666F, bounded)
                .speed(0.0F, speedY).randomizedSeed();
    }

    private static DynamicLayer layer(float radialScale, float sourceLength, float sourcePosition,
                                       DynamicMaterial material, Curve brightness, Curve length) {
        return new DynamicLayer(radialScale, sourceLength, sourcePosition, material, brightness, length);
    }

    private static BillboardLayer billboard(String texture, float scaleX, float scaleY,
                                            float scaleZ,
                                            float red, float green, float blue,
                                            float directionScale, Curve brightness,
                                            Curve randomCurve) {
        return new BillboardLayer(Wildfires.rl("textures/effect/" + texture), scaleX, scaleY,
                scaleZ, 0.0F, red, green, blue, directionScale, brightness, randomCurve);
    }

    private static Key key(float time, float value, float inTangent, float outTangent) {
        return new Key(time, value, inTangent, outTangent);
    }

    private static Curve curve(Key... keys) {
        return new Curve(keys);
    }

    private record DynamicLayer(float radialScale, float sourceLength, float sourcePosition,
                                 DynamicMaterial material, Curve brightnessCurve,
                                 Curve lengthCurve) {
    }

    private record BillboardLayer(ResourceLocation texture, float sourceScaleX, float sourceScaleY,
                                  float sourceScaleZ, float sourcePosition,
                                  float red, float green, float blue,
                                  float directionScale,
                                  Curve brightnessCurve, Curve randomCurve) {
    }

    private record Key(float time, float value, float inTangent, float outTangent) {
    }

    private record Curve(Key[] keys) {
        private float evaluate(float value) {
            if (value <= keys[0].time) return keys[0].value;
            for (int index = 0; index < keys.length - 1; index++) {
                Key left = keys[index];
                Key right = keys[index + 1];
                if (value > right.time) continue;
                float width = right.time - left.time;
                float t = (value - left.time) / width;
                float t2 = t * t;
                float t3 = t2 * t;
                float h00 = 2.0F * t3 - 3.0F * t2 + 1.0F;
                float h10 = t3 - 2.0F * t2 + t;
                float h01 = -2.0F * t3 + 3.0F * t2;
                float h11 = t3 - t2;
                return h00 * left.value + h10 * left.outTangent * width
                        + h01 * right.value + h11 * right.inTangent * width;
            }
            return keys[keys.length - 1].value;
        }
    }

    private static final class DynamicMaterial {
        private final ResourceLocation texture;
        private float startR = 1.0F;
        private float startG = 1.0F;
        private float startB = 1.0F;
        private float endR = 1.0F;
        private float endG = 1.0F;
        private float endB = 1.0F;
        private float tintFalloff;
        private float falloff;
        private float fresnel;
        private float fresnelInvert;
        private float noise;
        private float fadeIn;
        private float fadeOut;
        private float expandOffset;
        private float expandLinear;
        private float expandSquare;
        private float expandBounded;
        private float falloffStart;
        private float symmetry;
        private float symmetryStrength = 1.0F;
        private float seed = 1.0F;
        private float speedX;
        private float speedY = 1.0F;
        private float tileX = 1.0F;
        private float tileY = 1.0F;
        private boolean randomizedSeed;

        private DynamicMaterial(ResourceLocation texture) {
            this.texture = texture;
        }

        private DynamicMaterial tint(float sr, float sg, float sb, float er, float eg, float eb) {
            startR = sr;
            startG = sg;
            startB = sb;
            endR = er;
            endG = eg;
            endB = eb;
            return this;
        }

        private DynamicMaterial tintFalloff(float value) { tintFalloff = value; return this; }
        private DynamicMaterial falloff(float value) { falloff = value; return this; }
        private DynamicMaterial fresnel(float value) { fresnel = value; return this; }
        private DynamicMaterial noise(float value) { noise = value; return this; }
        private DynamicMaterial fade(float in, float out) { fadeIn = in; fadeOut = out; return this; }
        private DynamicMaterial expand(float offset, float linear, float square, float bounded) {
            expandOffset = offset;
            expandLinear = linear;
            expandSquare = square;
            expandBounded = bounded;
            return this;
        }
        private DynamicMaterial speed(float x, float y) { speedX = x; speedY = y; return this; }
        private DynamicMaterial tile(float x, float y) { tileX = x; tileY = y; return this; }
        private DynamicMaterial randomizedSeed() { randomizedSeed = true; return this; }
    }

    private record DynamicBindings(ShaderInstance shader, AbstractUniform time,
                                   AbstractUniform modelScale, AbstractUniform startTint,
                                   AbstractUniform endTint, AbstractUniform tintFalloff,
                                   AbstractUniform falloff, AbstractUniform fresnel,
                                   AbstractUniform fresnelInvert, AbstractUniform noise,
                                   AbstractUniform brightness, AbstractUniform fadeIn,
                                   AbstractUniform fadeOut, AbstractUniform expandOffset,
                                   AbstractUniform expandLinear, AbstractUniform expandSquare,
                                   AbstractUniform expandBounded, AbstractUniform falloffStart,
                                   AbstractUniform symmetry, AbstractUniform symmetryStrength,
                                   AbstractUniform seed, AbstractUniform speedX,
                                   AbstractUniform speedY, AbstractUniform tileX,
                                   AbstractUniform tileY, AbstractUniform clipBrightness,
                                   AbstractUniform plumeDirection) implements Supplier<ShaderInstance> {
        private static DynamicBindings create(ShaderInstance shader) {
            return new DynamicBindings(shader, shader.safeGetUniform("Time"),
                    shader.safeGetUniform("ModelScale"), shader.safeGetUniform("StartTint"),
                    shader.safeGetUniform("EndTint"), shader.safeGetUniform("TintFalloff"),
                    shader.safeGetUniform("Falloff"), shader.safeGetUniform("Fresnel"),
                    shader.safeGetUniform("FresnelInvert"), shader.safeGetUniform("Noise"),
                    shader.safeGetUniform("Brightness"), shader.safeGetUniform("FadeIn"),
                    shader.safeGetUniform("FadeOut"), shader.safeGetUniform("ExpandOffset"),
                    shader.safeGetUniform("ExpandLinear"), shader.safeGetUniform("ExpandSquare"),
                    shader.safeGetUniform("ExpandBounded"), shader.safeGetUniform("FalloffStart"),
                    shader.safeGetUniform("Symmetry"), shader.safeGetUniform("SymmetryStrength"),
                    shader.safeGetUniform("Seed"), shader.safeGetUniform("SpeedX"),
                    shader.safeGetUniform("SpeedY"), shader.safeGetUniform("TileX"),
                    shader.safeGetUniform("TileY"), shader.safeGetUniform("ClipBrightness"),
                    shader.safeGetUniform("PlumeDirection"));
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }

    private record DirectionalBindings(ShaderInstance shader, AbstractUniform startTint,
                                       AbstractUniform scale, AbstractUniform direction,
                                       AbstractUniform cameraPosition, AbstractUniform directionScale,
                                       AbstractUniform brightness) implements Supplier<ShaderInstance> {
        private static DirectionalBindings create(ShaderInstance shader) {
            return new DirectionalBindings(shader, shader.safeGetUniform("StartTint"),
                    shader.safeGetUniform("Scale"), shader.safeGetUniform("Direction"),
                    shader.safeGetUniform("CameraPosition"),
                    shader.safeGetUniform("DirectionScale"),
                    shader.safeGetUniform("Brightness"));
        }

        @Override
        public ShaderInstance get() {
            return shader;
        }
    }
}
