/*
 * Adapted from NTM: Space SkyProviderCelestial.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires changes: applies the source altitude fade after Forge's final fog-colour hook so
 * biome fog cannot leave a blue clear colour around the reusable capsule above the atmosphere.
 */
package first.wildfires.mixin.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import first.wildfires.client.space.NtmAscentAtmosphereVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the final Forge clear/fog colour on the same NTM ascent curve as the sky dome. */
@Mixin(FogRenderer.class)
public abstract class NtmAscentFogRendererMixin {

    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void wildfires$fadeReturnCapsuleAscentFog(Camera camera, float partialTick,
                                                             ClientLevel level,
                                                             int renderDistanceChunks,
                                                             float bossColorModifier,
                                                             CallbackInfo callback) {
        float factor = NtmAscentAtmosphereVisuals.factor(level, camera);
        if (factor >= 1.0F) {
            return;
        }
        fogRed *= factor;
        fogGreen *= factor;
        fogBlue *= factor;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }
}
