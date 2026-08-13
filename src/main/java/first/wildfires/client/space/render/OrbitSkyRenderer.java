package first.wildfires.client.space.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import first.wildfires.Wildfires;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.client.celestial.CelestialClientStateCache;
import first.wildfires.client.space.OrbitVisualDebugClock;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import first.wildfires.space.celestial.CelestialDefinition;
import first.wildfires.space.celestial.CelestialDefinitionRegistry;
import first.wildfires.space.celestial.CelestialKind;
import first.wildfires.space.celestial.CelestialVisualDefinition;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationContextResolver;
import first.wildfires.thirdparty.genesisadapt.GenesisPlanetMesh;
import first.wildfires.thirdparty.genesisadapt.GenesisPlanetShader;
import net.dries007.tfc.util.calendar.Calendars;
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
            // Never reveal the orbit dimension's clear/fog colour during a missing or not-yet-synced
            // observation context. A black vacuum is truthful without inventing a station/body state.
            RenderSystem.depthMask(false);
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            try {
                NtmOrbitSkyRenderer.drawVacuumBackdrop(poseStack, projectionMatrix);
            } finally {
                VertexBuffer.unbind();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
                setupFog.run();
            }
            return;
        }

        double gameTime = OrbitVisualDebugClock.gameTime()
                .orElse(level.getGameTime() + partialTick);
        double calendarTicks = OrbitVisualDebugClock.calendarTicks()
                .orElse(celestial.calendarTicks());
        double calendarRate = OrbitVisualDebugClock.calendarTicks().isPresent()
                ? 0.0D : TfcCalendarRateController.clientMultiplier();
        OrbitVisualRules.Frame frame = OrbitVisualRules.frame(context, celestial, gameTime, calendarTicks,
                calendarRate, Calendars.get(level).getCalendarDaysInMonth());
        GenesisPlanetMesh.ensure();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        poseStack.pushPose();
        poseStack.mulPose(viewOrientation(frame));
        try {
            NtmOrbitSkyRenderer.drawNight(poseStack, projectionMatrix,
                    frame.illumination().starVisibility(), frame.relativity(), frame.velocityDirection());
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            boolean sunDrawn = false;
            for (OrbitVisualRules.BodyLayer body : frame.bodies()) {
                if (!sunDrawn && frame.sun().distance() > body.distance()) {
                    drawSunLayer(frame.sun(), poseStack);
                    sunDrawn = true;
                }
                drawBodyLayer(body, level, context, calendarTicks, poseStack, projectionMatrix);
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
                                      ObservationContext context,
                                      double calendarTicks,
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
                        calendarTicks, poseStack, projectionMatrix);
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
        RelativisticVisualRules.Tint tint = body.tint();
        surfaceShader.safeGetUniform("LayerColor").set((float) tint.red(), (float) tint.green(),
                (float) tint.blue(), 1.0F);
        surfaceShader.safeGetUniform("ReceiverRadius").set(1.0F);
        configureSatelliteShadows(surfaceShader, body, rotation, calendarTicks);
        GenesisPlanetMesh.drawSurface(poseStack, projectionMatrix);

        drawClouds(body, visual.clouds(), generation, rotation, calendarTicks, lightLocal,
                poseStack, projectionMatrix);
        drawAtmosphere(body, visual.atmosphere(), center, rotation, lightLocal,
                halfSize, calendarTicks, poseStack, projectionMatrix);
        poseStack.popPose();
    }

    private static void drawClouds(OrbitVisualRules.BodyLayer body,
                                   CelestialVisualDefinition.CloudLayer clouds,
                                   long generation, Quaternionf rotation, double calendarTicks,
                                   Vector3f lightLocal, PoseStack poseStack,
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
        RelativisticVisualRules.Tint relativityTint = body.tint();

        poseStack.pushPose();
        if (clouds.shadowStrength() > 0.001D) {
            drawCloudShell(shader, texture.location(), cloudLight, 1.001F,
                    0.0F, 0.0F, 0.0F,
                    (float) (clouds.opacity() * clouds.shadowStrength() * body.cubeAlpha()),
                    body, rotation, calendarTicks, poseStack, projectionMatrix);
        }
        drawCloudShell(shader, texture.location(), cloudLight,
                (float) clouds.radiusMultiplier(), (float) (tint.red() * relativityTint.red()),
                (float) (tint.green() * relativityTint.green()),
                (float) (tint.blue() * relativityTint.blue()),
                (float) (clouds.opacity() * body.cubeAlpha()),
                body, rotation, calendarTicks, poseStack, projectionMatrix);
        poseStack.popPose();
    }

    private static void drawCloudShell(ShaderInstance shader, ResourceLocation texture,
                                       Vector3f lightLocal, float scale,
                                       float red, float green, float blue, float alpha,
                                       OrbitVisualRules.BodyLayer body, Quaternionf rotation,
                                       double calendarTicks, PoseStack poseStack,
                                       Matrix4f projectionMatrix) {
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
        shader.safeGetUniform("ReceiverRadius").set(scale);
        configureSatelliteShadows(shader, body, rotation, calendarTicks);
        GenesisPlanetMesh.drawSurface(poseStack, projectionMatrix);
        RenderSystem.depthMask(true);
        poseStack.popPose();
    }

    private static void drawAtmosphere(OrbitVisualRules.BodyLayer body,
                                       CelestialVisualDefinition.Atmosphere atmosphere,
                                       Vec3 center, Quaternionf rotation, Vector3f lightLocal,
                                       float halfSize, double calendarTicks, PoseStack poseStack,
                                       Matrix4f projectionMatrix) {
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
        RelativisticVisualRules.Tint relativityTint = body.tint();

        poseStack.pushPose();
        poseStack.scale(thickness, thickness, thickness);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> shader);
        shader.safeGetUniform("CameraPosition").set(cameraLocal.x, cameraLocal.y, cameraLocal.z);
        shader.safeGetUniform("LightDirection").set(lightLocal.x, lightLocal.y, lightLocal.z);
        shader.safeGetUniform("DayColor").set((float) (dayColor.red() * relativityTint.red()),
                (float) (dayColor.green() * relativityTint.green()), (float) (dayColor.blue() * relativityTint.blue()));
        shader.safeGetUniform("SunsetColor").set((float) (sunsetColor.red() * relativityTint.red()),
                (float) (sunsetColor.green() * relativityTint.green()), (float) (sunsetColor.blue() * relativityTint.blue()));
        shader.safeGetUniform("NightColor").set((float) (nightColor.red() * relativityTint.red()),
                (float) (nightColor.green() * relativityTint.green()), (float) (nightColor.blue() * relativityTint.blue()));
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
        configureSatelliteShadows(shader, body, rotation, calendarTicks);
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
    static Quaternionf viewOrientation(OrbitVisualRules.Frame frame) {
        return OrbitVisualRules.frameViewOrientation(frame);
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

    /** Uploads a small fixed set of OBB casters; no shadow texture or framebuffer is allocated. */
    private static void configureSatelliteShadows(ShaderInstance shader,
                                                  OrbitVisualRules.BodyLayer body,
                                                  Quaternionf parentRotation,
                                                  double calendarTicks) {
        shader.safeGetUniform("ShadowCount").set(body.satelliteShadows().size());
        shader.safeGetUniform("SunHalfTangent").set((float) body.sunHalfTangent());
        Quaternionf parentInverse = new Quaternionf(parentRotation).conjugate();
        for (int index = 0; index < body.satelliteShadows().size(); index++) {
            OrbitVisualRules.SatelliteShadow shadow = body.satelliteShadows().get(index);
            Vector3f center = vector3f(shadow.relativePosition());
            parentInverse.transform(center);
            center.div((float) body.radius());

            Quaternionf satelliteRotation = bodyRotation(shadow.satellite(), calendarTicks);
            Vector3f axisX = new Vector3f(1.0F, 0.0F, 0.0F);
            Vector3f axisY = new Vector3f(0.0F, 1.0F, 0.0F);
            Vector3f axisZ = new Vector3f(0.0F, 0.0F, 1.0F);
            satelliteRotation.transform(axisX);
            satelliteRotation.transform(axisY);
            satelliteRotation.transform(axisZ);
            parentInverse.transform(axisX).normalize();
            parentInverse.transform(axisY).normalize();
            parentInverse.transform(axisZ).normalize();

            String suffix = Integer.toString(index);
            shader.safeGetUniform("ShadowCenter" + suffix).set(center.x, center.y, center.z);
            shader.safeGetUniform("ShadowHalfSize" + suffix).set((float) shadow.halfSize());
            shader.safeGetUniform("ShadowAxisX" + suffix).set(axisX.x, axisX.y, axisX.z);
            shader.safeGetUniform("ShadowAxisY" + suffix).set(axisY.x, axisY.y, axisY.z);
            shader.safeGetUniform("ShadowAxisZ" + suffix).set(axisZ.x, axisZ.y, axisZ.z);
        }
    }

    private static Vector3f vector3f(CelestialVector vector) {
        return new Vector3f((float) vector.x(), (float) vector.y(), (float) vector.z());
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
