package first.wildfires.client.space.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import first.wildfires.Wildfires;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.client.celestial.CelestialClientStateCache;
import first.wildfires.client.space.OrbitVisualDebugClock;
import first.wildfires.client.space.ReturnCapsuleClientTransition;
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
            ReturnCapsuleClientTransition.markOrbitSceneRendered(false, false, false);
            return;
        }
        ObservationContext context = ObservationContextResolver.resolve(level, camera.getPosition()).orElse(null);
        CelestialState celestial = CelestialClientStateCache.stateOrNull(
                level, camera.getPosition(), partialTick);
        if (context == null || celestial == null) {
            ReturnCapsuleClientTransition.markOrbitSceneRendered(
                    context != null, celestial != null, false);
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
        OrbitVisualRules.Frame frame = OrbitVisualFrameCache.frame(context, celestial, gameTime, calendarTicks,
                calendarRate, Calendars.get(level).getCalendarDaysInMonth());
        GenesisPlanetMesh.ensure();
        prewarmJumpTarget(level, context);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        poseStack.pushPose();
        poseStack.mulPose(viewOrientation(frame));
        boolean currentBodyRendered = false;
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
                boolean squareRendered = drawBodyLayer(body, level, context, calendarTicks,
                        poseStack, projectionMatrix);
                if (squareRendered && body.body().equals(context.currentBody())) {
                    currentBodyRendered = true;
                }
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
        ReturnCapsuleClientTransition.markOrbitSceneRendered(true, true, currentBodyRendered);
    }

    /**
     * Procedural cube atlases are deliberately generated before the ten-tick reveal. Surface work
     * is paid during direction search and cloud work during acceleration; arrival then only reads
     * cached GPU textures instead of losing most of its half-second animation to a synchronous bake.
     */
    private static void prewarmJumpTarget(ClientLevel level, ObservationContext context) {
        first.wildfires.space.celestial.ObservationJourney journey = context.journey().orElse(null);
        if (journey == null || journey.mode() != first.wildfires.space.route.StationTravelMode.JUMP
                || (journey.phase() != first.wildfires.space.station.StationJourneyPhase.DEPARTING
                && !journey.phase().isJumpPhase())) {
            return;
        }
        CelestialDefinition definition = CelestialDefinitionRegistry.get(level.registryAccess())
                .get(journey.toBody());
        if (definition == null || definition.kind() == CelestialKind.STAR
                || !definition.visual().nearBodyRenderer().equals(CUBE_RENDERER)) {
            return;
        }
        CelestialVisualDefinition visual = definition.visual();
        OrbitBodyTextureManager.surface(journey.toBody(), visual,
                context.celestialRegistryGeneration());
        if (journey.phase().isJumpPhase() && visual.clouds().enabled()) {
            OrbitBodyTextureManager.clouds(journey.toBody(), visual.clouds(),
                    context.celestialRegistryGeneration());
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
    }

    private static boolean drawBodyLayer(OrbitVisualRules.BodyLayer body, ClientLevel level,
                                         ObservationContext context,
                                         double calendarTicks,
                                         PoseStack poseStack, Matrix4f projectionMatrix) {
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        NtmOrbitSkyRenderer.drawPoint(body, poseStack);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        boolean depthWritten = false;
        if (body.cubeAlpha() > 0.001D) {
            CelestialDefinition definition = CelestialDefinitionRegistry.get(level.registryAccess())
                    .get(body.body());
            if (definition != null && definition.kind() != CelestialKind.STAR
                    && definition.visual().nearBodyRenderer().equals(CUBE_RENDERER)) {
                depthWritten = drawCubeBody(body, definition.visual(), context.celestialRegistryGeneration(),
                        calendarTicks, poseStack, projectionMatrix);
            }
        }
        if (depthWritten) {
            clearLayerDepth();
        }
        return depthWritten;
    }

    private static void clearLayerDepth() {
        RenderSystem.depthMask(true);
        RenderSystem.clear(0x00000100, Minecraft.ON_OSX);
    }

    private static boolean drawCubeBody(OrbitVisualRules.BodyLayer body,
                                        CelestialVisualDefinition visual, long generation,
                                        double calendarTicks,
                                        PoseStack poseStack, Matrix4f projectionMatrix) {
        GenesisPlanetShader.TexturedBindings surfaceShader = GenesisPlanetShader.texturedBindings();
        if (surfaceShader == null) {
            return false;
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
        RenderSystem.setShader(surfaceShader);
        RenderSystem.setShaderTexture(0, surface.location());
        surfaceShader.lightDirection().set(lightLocal.x, lightLocal.y, lightLocal.z);
        surfaceShader.alpha().set((float) body.cubeAlpha());
        RelativisticVisualRules.Tint tint = body.tint();
        surfaceShader.layerColor().set((float) tint.red(), (float) tint.green(),
                (float) tint.blue(), 1.0F);
        surfaceShader.receiverRadius().set(1.0F);
        configureSatelliteShadows(surfaceShader.satelliteShadows(), body, rotation, calendarTicks);
        GenesisPlanetMesh.drawSurface(poseStack, projectionMatrix);

        drawClouds(body, surfaceShader, visual.clouds(), generation, rotation, calendarTicks, lightLocal,
                poseStack, projectionMatrix);
        drawAtmosphere(body, visual.atmosphere(), center, rotation, lightLocal,
                halfSize, calendarTicks, poseStack, projectionMatrix);
        poseStack.popPose();
        return true;
    }

    private static void drawClouds(OrbitVisualRules.BodyLayer body,
                                   GenesisPlanetShader.TexturedBindings shader,
                                   CelestialVisualDefinition.CloudLayer clouds,
                                   long generation, Quaternionf rotation, double calendarTicks,
                                   Vector3f lightLocal, PoseStack poseStack,
                                   Matrix4f projectionMatrix) {
        if (!clouds.enabled() || clouds.opacity() <= 0.001D) {
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

    private static void drawCloudShell(GenesisPlanetShader.TexturedBindings shader,
                                       ResourceLocation texture,
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
        RenderSystem.setShader(shader);
        RenderSystem.setShaderTexture(0, texture);
        shader.lightDirection().set(lightLocal.x, lightLocal.y, lightLocal.z);
        shader.alpha().set(alpha);
        shader.layerColor().set(red, green, blue, 1.0F);
        shader.receiverRadius().set(scale);
        configureSatelliteShadows(shader.satelliteShadows(), body, rotation, calendarTicks);
        GenesisPlanetMesh.drawSurface(poseStack, projectionMatrix);
        RenderSystem.depthMask(true);
        poseStack.popPose();
    }

    private static void drawAtmosphere(OrbitVisualRules.BodyLayer body,
                                       CelestialVisualDefinition.Atmosphere atmosphere,
                                       Vec3 center, Quaternionf rotation, Vector3f lightLocal,
                                       float halfSize, double calendarTicks, PoseStack poseStack,
                                       Matrix4f projectionMatrix) {
        GenesisPlanetShader.AtmosphereBindings shader = GenesisPlanetShader.atmosphereBindings();
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
        RenderSystem.setShader(shader);
        shader.cameraPosition().set(cameraLocal.x, cameraLocal.y, cameraLocal.z);
        shader.lightDirection().set(lightLocal.x, lightLocal.y, lightLocal.z);
        shader.dayColor().set((float) (dayColor.red() * relativityTint.red()),
                (float) (dayColor.green() * relativityTint.green()), (float) (dayColor.blue() * relativityTint.blue()));
        shader.sunsetColor().set((float) (sunsetColor.red() * relativityTint.red()),
                (float) (sunsetColor.green() * relativityTint.green()), (float) (sunsetColor.blue() * relativityTint.blue()));
        shader.nightColor().set((float) (nightColor.red() * relativityTint.red()),
                (float) (nightColor.green() * relativityTint.green()), (float) (nightColor.blue() * relativityTint.blue()));
        shader.regionBrightness().set((float) atmosphere.dayBrightness(),
                (float) atmosphere.sunsetBrightness(), (float) atmosphere.nightBrightness());
        shader.lightTransitions().set((float) atmosphere.dayTransition(),
                (float) atmosphere.nightTransition());
        shader.limbParameters().set((float) atmosphere.limbStrength(),
                (float) atmosphere.limbPower());
        shader.opacityExposure().set((float) atmosphere.maxOpacity(),
                (float) atmosphere.exposure());
        shader.atmosphereThickness().set(thickness);
        shader.density().set(density);
        shader.alpha().set((float) body.cubeAlpha());
        configureSatelliteShadows(shader.satelliteShadows(), body, rotation, calendarTicks);
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
    private static void configureSatelliteShadows(
                                                  GenesisPlanetShader.SatelliteShadowBindings shader,
                                                  OrbitVisualRules.BodyLayer body,
                                                  Quaternionf parentRotation,
                                                  double calendarTicks) {
        configureSatelliteShadows(shader, body.radius(), body.sunHalfTangent(),
                body.satelliteShadows(), parentRotation, calendarTicks);
    }

    static void configureSatelliteShadows(
            GenesisPlanetShader.SatelliteShadowBindings shader,
            OrbitVisualRules.SatelliteShadowFrame frame,
            Quaternionf parentRotation, double calendarTicks) {
        configureSatelliteShadows(shader, frame.parentRadius(), frame.sunHalfTangent(),
                frame.shadows(), parentRotation, calendarTicks);
    }

    private static void configureSatelliteShadows(
            GenesisPlanetShader.SatelliteShadowBindings shader,
            double parentRadius, double sunHalfTangent,
            java.util.List<OrbitVisualRules.SatelliteShadow> shadows,
            Quaternionf parentRotation, double calendarTicks) {
        shader.shadowCount().set(shadows.size());
        shader.sunHalfTangent().set((float) sunHalfTangent);
        Quaternionf parentInverse = new Quaternionf(parentRotation).conjugate();
        for (int index = 0; index < shadows.size(); index++) {
            OrbitVisualRules.SatelliteShadow shadow = shadows.get(index);
            Vector3f center = vector3f(shadow.relativePosition());
            parentInverse.transform(center);
            center.div((float) parentRadius);

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

            shader.center(index).set(center.x, center.y, center.z);
            shader.halfSize(index).set((float) shadow.halfSize());
            shader.axisX(index).set(axisX.x, axisX.y, axisX.z);
            shader.axisY(index).set(axisY.x, axisY.y, axisY.z);
            shader.axisZ(index).set(axisZ.x, axisZ.y, axisZ.z);
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
        OrbitVisualFrameCache.reset();
        GenesisPlanetMesh.close();
        NtmOrbitSkyRenderer.close();
        OrbitBodyTextureManager.reset();
    }
}
