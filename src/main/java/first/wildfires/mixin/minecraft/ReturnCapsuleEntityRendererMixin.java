/*
 * Adapted from NTM: Space EntityRideableRocket's rigid rider-position contract.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: carries the corrected passenger render through modern frustum gating.
 */
package first.wildfires.mixin.minecraft;

import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A rigidly corrected passenger must remain visible whenever its tracked capsule renders. */
@Mixin(EntityRenderer.class)
public abstract class ReturnCapsuleEntityRendererMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void wildfires$keepReturnCapsulePassengerVisible(Entity entity, Frustum frustum,
                                                              double cameraX, double cameraY,
                                                              double cameraZ,
                                                              CallbackInfoReturnable<Boolean> callback) {
        if (entity.getVehicle() instanceof ReusableReturnCapsuleEntity capsule
                && capsule.hasPassenger(entity) && !capsule.isRemoved()) {
            callback.setReturnValue(true);
        }
    }
}
