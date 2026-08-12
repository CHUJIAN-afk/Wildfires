package first.wildfires.client.space.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import first.wildfires.Wildfires;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.client.celestial.CelestialClientStateCache;
import first.wildfires.space.celestial.CelestialDefinition;
import first.wildfires.space.celestial.CelestialDefinitionRegistry;
import first.wildfires.space.celestial.CelestialKind;
import first.wildfires.space.celestial.CelestialVisualDefinition;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationContextResolver;
import first.wildfires.thirdparty.genesisadapt.GenesisPlanetMesh;
import first.wildfires.thirdparty.genesisadapt.GenesisPlanetShader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** NTM deep-space layers plus Genesis-derived square near-body rendering for the sole orbit level. */
public final class OrbitSkyRenderer {

    private static final ResourceLocation CUBE_RENDERER = Wildfires.rl("cube");

    private OrbitSkyRenderer() {
    }

    public static void render(ClientLevel level, float partialTick, PoseStack poseStack, Camera camera,
                              Matrix4f projectionMatrix, boolean foggy, Runnable setupFog) {
        setupFog.run();
        if (foggy || camera.getFluidInCamera() == FogType.POWDER_SNOW
                || camera.getFluidInCamera() == FogType.LAVA || blockedByEffect(camera)) {
            return;
        }
        ObservationContext context = ObservationContextResolver.resolve(level, camera.getPosition()).orElse(null);
        CelestialState celestial = CelestialClientStateCache.state(
                level, camera.getPosition(), partialTick).orElse(null);
        if (context == null || celestial == null) {
            return;
        }

        double gameTime = level.getGameTime() + partialTick;
        OrbitVisualRules.Frame frame = OrbitVisualRules.frame(context, celestial, gameTime);
        GenesisPlanetMesh.ensure();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        poseStack.pushPose();
        poseStack.mulPose(viewOrientation(frame.viewRotationRadians()));
        try {
            NtmOrbitSkyRenderer.drawNight(poseStack, projectionMatrix,
                    frame.illumination().starVisibility());
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            boolean sunDrawn = false;
            for (OrbitVisualRules.BodyLayer body : frame.bodies()) {
                if (!sunDrawn && frame.sun().distance() > body.distance()) {
                    drawSunLayer(frame.sun(), poseStack);
                    sunDrawn = true;
                }
                drawBodyLayer(body, level, context, celestial, poseStack, projectionMatrix);
            }
            if (!sunDrawn) {
                drawSunLayer(frame.sun(), poseStack);
            }
        } finally {
            poseStack.popPose();
            VertexBuffer.unbind();
            // Bodies share a sky-depth buffer only for their own surface/atmosphere ordering.
            RenderSystem.clear(0x00000100, Minecraft.ON_OSX);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            setupFog.run();
        }
    }

    private static void drawSunLayer(OrbitVisualRules.SunLayer sun, PoseStack poseStack) {
        RenderSystem.disableCull();
        // NTM orders the black star mask, corona and photosphere explicitly with depth writes off.
        // Letting the three transparent quads share a depth plane causes the striped flicker seen
        // on modern drivers. Inter-body occlusion is already guaranteed by the far-to-near pass.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        NtmOrbitSkyRenderer.drawSun(sun, poseStack);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        clearLayerDepth();
    }

    private static void drawBodyLayer(OrbitVisualRules.BodyLayer body, ClientLevel level,
                                      ObservationContext context, CelestialState celestial,
                                      PoseStack poseStack, Matrix4f projectionMatrix) {
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        NtmOrbitSkyRenderer.drawPoint(body, poseStack);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        if (body.cubeAlpha() > 0.001D) {
            CelestialDefinition definition = CelestialDefinitionRegistry.get(level.registryAccess())
                    .get(body.body());
            if (definition != null && definition.kind() != CelestialKind.STAR
                    && definition.visual().nearBodyRenderer().equals(CUBE_RENDERER)) {
                drawCubeBody(body, definition.visual(), context.celestialRegistryGeneration(),
                        celestial.calendarTicks(), poseStack, projectionMatrix);
            }
        }
        clearLayerDepth();
    }

    private static void clearLayerDepth() {
        RenderSystem.depthMask(true);
        RenderSystem.clear(0x00000100, Minecraft.ON_OSX);
    }

    private static void drawCubeBody(OrbitVisualRules.BodyLayer body,
                                     CelestialVisualDefinition visual, long generation,
                                     double calendarTicks,
                                     PoseStack poseStack, Matrix4f projectionMatrix) {
        ShaderInstance surfaceShader = GenesisPlanetShader.textured();
        if (surfaceShader == null) {
            return;
        }
        OrbitBodyTextureManager.ResolvedTexture surface = OrbitBodyTextureManager.surface(
                body.body(), visual, generation);
        Vec3 center = vector(body.direction()).scale(body.renderDistance());
        Quaternionf rotation = bodyRotation(body.body(), calendarTicks);
        Vector3f lightLocal = localVector(body.incomingLightDirection(), rotation);
        float halfSize = (float) body.renderHalfSize();

        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        poseStack.mulPose(rotation);
        poseStack.scale(halfSize, halfSize, halfSize);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> surfaceShader);
        RenderSystem.setShaderTexture(0, surface.location());
        surfaceShader.safeGetUniform("LightDirection").set(lightLocal.x, lightLocal.y, lightLocal.z);
        surfaceShader.safeGetUniform("Alpha").set((float) body.cubeAlpha());
        surfaceShader.safeGetUniform("LayerColor").set(1.0F, 1.0F, 1.0F, 1.0F);
        GenesisPlanetMesh.drawSurface(poseStack, projectionMatrix);

        drawClouds(body, visual.clouds(), generation, lightLocal,
                poseStack, projectionMatrix);
        drawAtmosphere(body, visual.atmosphere(), center, rotation, lightLocal,
                halfSize, poseStack, projectionMatrix);
        poseStack.popPose();
    }

    private static void drawClouds(OrbitVisualRules.BodyLayer body,
                                   CelestialVisualDefinition.CloudLayer clouds,
                                   long generation, Vector3f lightLocal, PoseStack poseStack,
                                   Matrix4f projectionMatrix) {
        ShaderInstance shader = GenesisPlanetShader.textured();
        if (shader == null || !clouds.enabled() || clouds.opacity() <= 0.001D) {
            return;
        }
        OrbitBodyTextureManager.ResolvedTexture texture = OrbitBodyTextureManager.clouds(
                body.body(), clouds, generation);
        // Cubic cloud geometry must stay rigidly aligned with the cubic planet. The configured
        // motion remains available through cloudTexturePhase for the future material cloud pass.
        Vector3f cloudLight = new Vector3f(lightLocal);
        CelestialVisualDefinition.Color tint = clouds.tint();

        poseStack.pushPose();
        if (clouds.shadowStrength() > 0.001D) {
            drawCloudShell(shader, texture.location(), cloudLight, 1.001F,
                    0.0F, 0.0F, 0.0F,
                    (float) (clouds.opacity() * clouds.shadowStrength() * body.cubeAlpha()),
                    poseStack, projectionMatrix);
        }
        drawCloudShell(shader, texture.location(), cloudLight,
                (float) clouds.radiusMultiplier(), (float) tint.red(), (float) tint.green(),
                (float) tint.blue(), (float) (clouds.opacity() * body.cubeAlpha()),
                poseStack, projectionMatrix);
        poseStack.popPose();
    }

    private static void drawCloudShell(ShaderInstance shader, ResourceLocation texture,
                                       Vector3f lightLocal, float scale,
                                       float red, float green, float blue, float alpha,
                                       PoseStack poseStack, Matrix4f projectionMatrix) {
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
        shader.safeGetUniform("LightDirection").set(lightLocal.x, lightLocal.y, lightLocal.z);
        shader.safeGetUniform("Alpha").set(alpha);
        shader.safeGetUniform("LayerColor").set(red, green, blue, 1.0F);
        GenesisPlanetMesh.drawSurface(poseStack, projectionMatrix);
        RenderSystem.depthMask(true);
        poseStack.popPose();
    }

    private static void drawAtmosphere(OrbitVisualRules.BodyLayer body,
                                       CelestialVisualDefinition.Atmosphere atmosphere,
                                       Vec3 center, Quaternionf rotation, Vector3f lightLocal,
                                       float halfSize, PoseStack poseStack, Matrix4f projectionMatrix) {
        ShaderInstance shader = GenesisPlanetShader.atmosphere();
        if (shader == null || !atmosphere.enabled()) {
            return;
        }
        Vector3f cameraLocal = new Vector3f((float) -center.x, (float) -center.y, (float) -center.z)
                .div(halfSize);
        new Quaternionf(rotation).conjugate().transform(cameraLocal);
        CelestialVisualDefinition.Color dayColor = atmosphere.color();
        CelestialVisualDefinition.Color sunsetColor = atmosphere.resolvedSunsetColor();
        CelestialVisualDefinition.Color nightColor = atmosphere.resolvedNightColor();
        float thickness = (float) atmosphere.radiusMultiplier();
        float density = (float) atmosphere.density();

        poseStack.pushPose();
        poseStack.scale(thickness, thickness, thickness);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        shader.safeGetUniform("CameraPosition").set(cameraLocal.x, cameraLocal.y, cameraLocal.z);
        shader.safeGetUniform("LightDirection").set(lightLocal.x, lightLocal.y, lightLocal.z);
        shader.safeGetUniform("DayColor").set((float) dayColor.red(), (float) dayColor.green(),
                (float) dayColor.blue());
        shader.safeGetUniform("SunsetColor").set((float) sunsetColor.red(), (float) sunsetColor.green(),
                (float) sunsetColor.blue());
        shader.safeGetUniform("NightColor").set((float) nightColor.red(), (float) nightColor.green(),
                (float) nightColor.blue());
        shader.safeGetUniform("RegionBrightness").set((float) atmosphere.dayBrightness(),
                (float) atmosphere.sunsetBrightness(), (float) atmosphere.nightBrightness());
        shader.safeGetUniform("LightTransitions").set((float) atmosphere.dayTransition(),
                (float) atmosphere.nightTransition());
        shader.safeGetUniform("LimbParameters").set((float) atmosphere.limbStrength(),
                (float) atmosphere.limbPower());
        shader.safeGetUniform("OpacityExposure").set((float) atmosphere.maxOpacity(),
                (float) atmosphere.exposure());
        shader.safeGetUniform("AtmosphereThickness").set(thickness);
        shader.safeGetUniform("Density").set(density);
        shader.safeGetUniform("Alpha").set((float) body.cubeAlpha());
        GenesisPlanetMesh.drawAtmosphere(poseStack, projectionMatrix);
        RenderSystem.depthMask(true);
        poseStack.popPose();
    }

    private static Quaternionf bodyRotation(ResourceLocation body, double calendarTicks) {
        return OrbitVisualRules.bodyRotation(body, calendarTicks);
    }

    /*
     * Adapt NTM Space's orbit-sky frame to the explicit Wildfires deck contract: 90 degrees about
     * X makes player +Y exactly parallel to ecliptic north, then -90 about Y maps the source frame.
     * Heading remains zero in ordinary orbit so the already-moving observer produces a visible
     * 8000-tick circuit; only departure/cruise/arrival apply an additional route heading.
     */
    static Quaternionf viewOrientation(double headingRadians) {
        return OrbitVisualRules.stationViewOrientation(headingRadians);
    }

    private static Vector3f localVector(CelestialVector vector,
                                        Quaternionf rotation) {
        Vector3f local = new Vector3f((float) vector.x(), (float) vector.y(), (float) vector.z());
        new Quaternionf(rotation).conjugate().transform(local);
        if (local.lengthSquared() < 1.0E-10F) {
            local.set(0.0F, 0.0F, 1.0F);
        } else {
            local.normalize();
        }
        return local;
    }

    private static Vec3 vector(CelestialVector vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static boolean blockedByEffect(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    public static void close() {
        RenderSystem.assertOnRenderThread();
        GenesisPlanetMesh.close();
        NtmOrbitSkyRenderer.close();
        OrbitBodyTextureManager.reset();
    }
}
