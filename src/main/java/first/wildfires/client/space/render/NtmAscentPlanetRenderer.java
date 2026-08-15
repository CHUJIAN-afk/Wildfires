/*
 * Underfoot-body behavior adapted from NTM: Space SkyProviderCelestial.
 * Copyright NTM: Space contributors. SPDX-License-Identifier: LGPL-3.0-only
 * Square mesh/shader presentation adapted from VS: Genesis PlanetRenderer/PlanetAtmosphereRenderer.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236. Apache-2.0 notice retained.
 * Wildfires changes: replaces NTM's flat body.texture quad with the synchronized Genesis-style
 * bound body's cube, atmosphere and rigid cloud shell while retaining the pod altitude gate.
 */
package first.wildfires.client.space.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import first.wildfires.Wildfires;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.client.space.NtmAscentAtmosphereVisuals;
import first.wildfires.space.celestial.CelestialDefinition;
import first.wildfires.space.celestial.CelestialDefinitionRegistry;
import first.wildfires.space.celestial.CelestialKind;
import first.wildfires.space.celestial.CelestialVisualDefinition;
import first.wildfires.thirdparty.genesisadapt.GenesisPlanetMesh;
import first.wildfires.thirdparty.genesisadapt.GenesisPlanetShader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws NTM's high-altitude reveal as the bound body's actual Genesis square planet. */
public final class NtmAscentPlanetRenderer {

    private static final ResourceLocation CUBE_RENDERER = Wildfires.rl("cube");

    private NtmAscentPlanetRenderer() {
    }

    public static void render(ClientLevel level, CelestialState state, Camera camera,
                              PoseStack poseStack, Matrix4f projectionMatrix) {
        NtmAscentAtmosphereVisuals.AscentFrame ascent =
                NtmAscentAtmosphereVisuals.frame(level, camera).orElse(null);
        if (ascent == null || ascent.profile().revealAlpha(ascent.altitude()) <= 0.0D) return;
        ResourceLocation bodyId = ascent.bodyId();

        Registry<CelestialDefinition> registry = CelestialDefinitionRegistry.get(level.registryAccess());
        CelestialDefinition definition = ascent.definition();
        if (definition == null || definition.kind() == CelestialKind.STAR
                || !definition.visual().nearBodyRenderer().equals(CUBE_RENDERER)) return;
        GenesisPlanetShader.TexturedBindings surfaceShader = GenesisPlanetShader.texturedBindings();
        if (surfaceShader == null) return;

        GenesisPlanetMesh.ensure();
        double altitude = ascent.altitude();
        float halfSize = (float) ascent.profile().planetHalfSize();
        Quaternionf rotation = OrbitVisualRules.bodyRotation(bodyId, state.calendarTicks());
        OrbitVisualRules.SatelliteShadowFrame shadowFrame =
                OrbitVisualRules.satelliteShadowFrame(bodyId, state);
        double outerShellExit = NtmAscentAtmosphereVisuals.outerShellExitDistance(rotation,
                NtmAscentAtmosphereVisuals.outerShellRadiusMultiplier(definition.visual()), halfSize);
        double centerDistance = NtmAscentAtmosphereVisuals.planetCenterDistance(
                altitude, outerShellExit, ascent.profile());
        float alpha = (float) NtmAscentAtmosphereVisuals.planetAlpha(
                altitude, centerDistance, outerShellExit, ascent.profile());
        if (alpha <= 0.001F) return;
        Vec3 center = new Vec3(0.0D,
                -centerDistance, 0.0D);
        Vector3f lightLocal = localIncomingLight(
                OrbitVisualRules.incomingLightDirection(bodyId, state), rotation);
        long textureGeneration = Integer.toUnsignedLong(System.identityHashCode(registry));

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        poseStack.pushPose();
        try {
            poseStack.translate(center.x, center.y, center.z);
            poseStack.mulPose(rotation);
            poseStack.scale(halfSize, halfSize, halfSize);
            drawSurface(bodyId, surfaceShader, definition.visual(), textureGeneration, lightLocal,
                    alpha, shadowFrame, rotation, state.calendarTicks(), poseStack, projectionMatrix);
            drawClouds(bodyId, surfaceShader, definition.visual().clouds(), textureGeneration,
                    lightLocal, alpha, shadowFrame, rotation, state.calendarTicks(),
                    poseStack, projectionMatrix);
            drawAtmosphere(definition.visual().atmosphere(), center, rotation, lightLocal,
                    halfSize, alpha, shadowFrame, state.calendarTicks(), poseStack, projectionMatrix);
        } finally {
            poseStack.popPose();
            VertexBuffer.unbind();
            RenderSystem.clear(0x00000100, Minecraft.ON_OSX);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            // This renderer runs at Forge AFTER_SKY, immediately before chunks and entities.
            // Restore the ordinary world-render state; leaving sky-style no-depth/no-cull state
            // makes the pod shell, four airbrakes and passenger draw through one another.
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void drawSurface(ResourceLocation bodyId,
                                    GenesisPlanetShader.TexturedBindings shader,
                                    CelestialVisualDefinition visual,
                                    long generation, Vector3f lightLocal, float alpha,
                                    OrbitVisualRules.SatelliteShadowFrame shadowFrame,
                                    Quaternionf rotation, double calendarTicks,
                                    PoseStack poseStack, Matrix4f projectionMatrix) {
        OrbitBodyTextureManager.ResolvedTexture surface = OrbitBodyTextureManager.surface(
                bodyId, visual, generation);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(shader);
        RenderSystem.setShaderTexture(0, surface.location());
        configureTexturedShader(shader, lightLocal, 1.0F, 1.0F, 1.0F, alpha, 1.0F,
                shadowFrame, rotation, calendarTicks);
        GenesisPlanetMesh.drawSurface(poseStack, projectionMatrix);
    }

    private static void drawClouds(ResourceLocation bodyId,
                                   GenesisPlanetShader.TexturedBindings shader,
                                   CelestialVisualDefinition.CloudLayer clouds, long generation,
                                   Vector3f lightLocal, float bodyAlpha,
                                   OrbitVisualRules.SatelliteShadowFrame shadowFrame,
                                   Quaternionf rotation, double calendarTicks,
                                   PoseStack poseStack, Matrix4f projectionMatrix) {
        if (!clouds.enabled() || clouds.opacity() <= 0.001D) return;
        ResourceLocation texture = OrbitBodyTextureManager.clouds(bodyId, clouds, generation).location();
        if (clouds.shadowStrength() > 0.001D) {
            drawCloudShell(shader, texture, lightLocal, 1.001F, 0.0F, 0.0F, 0.0F,
                    (float) (bodyAlpha * clouds.opacity() * clouds.shadowStrength()),
                    shadowFrame, rotation, calendarTicks,
                    poseStack, projectionMatrix);
        }
        CelestialVisualDefinition.Color tint = clouds.tint();
        drawCloudShell(shader, texture, lightLocal, (float) clouds.radiusMultiplier(),
                (float) tint.red(), (float) tint.green(), (float) tint.blue(),
                (float) (bodyAlpha * clouds.opacity()), shadowFrame, rotation, calendarTicks,
                poseStack, projectionMatrix);
    }

    private static void drawCloudShell(GenesisPlanetShader.TexturedBindings shader,
                                       ResourceLocation texture,
                                       Vector3f lightLocal, float scale,
                                       float red, float green, float blue, float alpha,
                                       OrbitVisualRules.SatelliteShadowFrame shadowFrame,
                                       Quaternionf rotation, double calendarTicks,
                                       PoseStack poseStack, Matrix4f projectionMatrix) {
        poseStack.pushPose();
        try {
            poseStack.scale(scale, scale, scale);
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(shader);
            RenderSystem.setShaderTexture(0, texture);
            configureTexturedShader(shader, lightLocal, red, green, blue, alpha, scale,
                    shadowFrame, rotation, calendarTicks);
            GenesisPlanetMesh.drawSurface(poseStack, projectionMatrix);
        } finally {
            RenderSystem.depthMask(true);
            poseStack.popPose();
        }
    }

    private static void drawAtmosphere(CelestialVisualDefinition.Atmosphere atmosphere,
                                       Vec3 center, Quaternionf rotation, Vector3f lightLocal,
                                       float halfSize, float alpha,
                                       OrbitVisualRules.SatelliteShadowFrame shadowFrame,
                                       double calendarTicks,
                                       PoseStack poseStack, Matrix4f projectionMatrix) {
        GenesisPlanetShader.AtmosphereBindings shader = GenesisPlanetShader.atmosphereBindings();
        if (shader == null || !atmosphere.enabled()) return;
        Vector3f cameraLocal = new Vector3f((float) -center.x, (float) -center.y, (float) -center.z)
                .div(halfSize);
        new Quaternionf(rotation).conjugate().transform(cameraLocal);
        CelestialVisualDefinition.Color day = atmosphere.color();
        CelestialVisualDefinition.Color sunset = atmosphere.resolvedSunsetColor();
        CelestialVisualDefinition.Color night = atmosphere.resolvedNightColor();
        float thickness = (float) atmosphere.radiusMultiplier();

        poseStack.pushPose();
        try {
            poseStack.scale(thickness, thickness, thickness);
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(shader);
            shader.cameraPosition().set(cameraLocal.x, cameraLocal.y, cameraLocal.z);
            shader.lightDirection().set(lightLocal.x, lightLocal.y, lightLocal.z);
            shader.dayColor().set((float) day.red(), (float) day.green(), (float) day.blue());
            shader.sunsetColor().set((float) sunset.red(), (float) sunset.green(),
                    (float) sunset.blue());
            shader.nightColor().set((float) night.red(), (float) night.green(),
                    (float) night.blue());
            shader.regionBrightness().set((float) atmosphere.dayBrightness(),
                    (float) atmosphere.sunsetBrightness(), (float) atmosphere.nightBrightness());
            shader.lightTransitions().set((float) atmosphere.dayTransition(),
                    (float) atmosphere.nightTransition());
            shader.limbParameters().set((float) atmosphere.limbStrength(),
                    (float) atmosphere.limbPower());
            shader.opacityExposure().set((float) atmosphere.maxOpacity(),
                    (float) atmosphere.exposure());
            shader.atmosphereThickness().set(thickness);
            shader.density().set((float) atmosphere.density());
            shader.alpha().set(alpha);
            OrbitSkyRenderer.configureSatelliteShadows(shader.satelliteShadows(), shadowFrame,
                    rotation, calendarTicks);
            GenesisPlanetMesh.drawAtmosphere(poseStack, projectionMatrix);
        } finally {
            RenderSystem.depthMask(true);
            poseStack.popPose();
        }
    }

    private static void configureTexturedShader(GenesisPlanetShader.TexturedBindings shader,
                                                Vector3f lightLocal,
                                                float red, float green, float blue,
                                                float alpha, float receiverRadius,
                                                OrbitVisualRules.SatelliteShadowFrame shadowFrame,
                                                Quaternionf rotation, double calendarTicks) {
        shader.lightDirection().set(lightLocal.x, lightLocal.y, lightLocal.z);
        shader.alpha().set(alpha);
        shader.layerColor().set(red, green, blue, 1.0F);
        shader.receiverRadius().set(receiverRadius);
        OrbitSkyRenderer.configureSatelliteShadows(shader.satelliteShadows(), shadowFrame,
                rotation, calendarTicks);
    }

    private static Vector3f localIncomingLight(CelestialVector incomingLight,
                                               Quaternionf rotation) {
        Vector3f light = new Vector3f((float) incomingLight.x(), (float) incomingLight.y(),
                (float) incomingLight.z());
        if (light.lengthSquared() < 1.0E-10F) {
            light.set(0.0F, 1.0F, 0.0F);
        } else {
            light.normalize();
        }
        new Quaternionf(rotation).conjugate().transform(light);
        return light;
    }
}
