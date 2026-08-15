/*
 * Adapted from NTM: Space EntityRideableRocket's rigid rider-position contract.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: binds Forge 1.20.1's complete player render at dispatcher entry.
 */
package first.wildfires.mixin.minecraft;

import first.wildfires.client.space.ReturnCapsulePassengerPose;
import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the whole player renderer, including Forge events and layers, use the capsule seat. */
@Mixin(PlayerRenderer.class)
public abstract class ReturnCapsulePlayerRendererMixin {

    @Inject(method = "getRenderOffset", at = @At("RETURN"), cancellable = true)
    private void wildfires$bindPlayerRenderToReturnCapsule(AbstractClientPlayer player,
                                                            float partialTick,
                                                            CallbackInfoReturnable<Vec3> callback) {
        if (player.getVehicle() instanceof ReusableReturnCapsuleEntity capsule
                && capsule.hasPassenger(player)) {
            callback.setReturnValue(callback.getReturnValue().add(
                    ReturnCapsulePassengerPose.passengerRenderOffset(
                            player, capsule, partialTick)));
        }
    }
}
