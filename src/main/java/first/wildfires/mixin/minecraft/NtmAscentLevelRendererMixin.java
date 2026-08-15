/*
 * Behavior adapted from NTM: Space SkyProviderCelestial's surface-to-orbit cloud boundary.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires changes: cancels only the local Minecraft/TFC cloud deck after the reusable pod has
 * crossed the square body's rendered surface; the Genesis orbital cloud shell remains untouched.
 */
package first.wildfires.mixin.minecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.client.space.NtmAscentAtmosphereVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class NtmAscentLevelRendererMixin {

    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true)
    private void wildfires$hideFlatSurfaceInsidePlanet(RenderType renderType, PoseStack poseStack,
                                                        double cameraX, double cameraY,
                                                        double cameraZ, Matrix4f projectionMatrix,
                                                        CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && NtmAscentAtmosphereVisuals.hideLocalTerrain(
                minecraft.level, minecraft.gameRenderer.getMainCamera())) {
            callback.cancel();
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void wildfires$hideLocalCloudDeck(PoseStack poseStack, Matrix4f projectionMatrix,
                                               float partialTick, double cameraX, double cameraY,
                                               double cameraZ, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && NtmAscentAtmosphereVisuals.hideLocalClouds(
                minecraft.level, minecraft.gameRenderer.getMainCamera())) {
            callback.cancel();
        }
    }

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void wildfires$hideLocalSurfaceWeather(LightTexture lightTexture, float partialTick,
                                                    double cameraX, double cameraY, double cameraZ,
                                                    CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && NtmAscentAtmosphereVisuals.hideLocalWeather(
                minecraft.level, minecraft.gameRenderer.getMainCamera())) {
            callback.cancel();
        }
    }

    @Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
    private void wildfires$stopLocalSurfaceWeatherParticles(Camera camera, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && NtmAscentAtmosphereVisuals.hideLocalWeather(minecraft.level, camera)) {
            callback.cancel();
        }
    }
}
