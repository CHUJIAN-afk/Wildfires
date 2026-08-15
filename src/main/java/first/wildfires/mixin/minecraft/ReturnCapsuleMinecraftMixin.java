/*
 * Adapted from VS: Genesis MinecraftMixin receiving-screen substitution.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236.
 * SPDX-License-Identifier: Apache-2.0
 * Wildfires changes: only intercepts an explicitly armed reusable-capsule transfer.
 */
package first.wildfires.mixin.minecraft;

import first.wildfires.client.space.ReturnCapsuleClientTransition;
import first.wildfires.client.space.ReturnCapsuleReceivingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ReturnCapsuleMinecraftMixin {

    @Unique
    private boolean wildfires$settingCapsuleScreen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void wildfires$replaceReceivingScreen(Screen screen, CallbackInfo callback) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (!wildfires$settingCapsuleScreen && ReturnCapsuleClientTransition.armed()
                && screen == null && minecraft.screen instanceof ReturnCapsuleReceivingScreen) {
            callback.cancel();
            return;
        }
        if (wildfires$settingCapsuleScreen || !ReturnCapsuleClientTransition.armed()
                || (!(screen instanceof ReceivingLevelScreen) && !(screen instanceof ProgressScreen))
                || screen instanceof ReturnCapsuleReceivingScreen) return;
        wildfires$settingCapsuleScreen = true;
        minecraft.setScreen(new ReturnCapsuleReceivingScreen());
        wildfires$settingCapsuleScreen = false;
        callback.cancel();
    }
}
