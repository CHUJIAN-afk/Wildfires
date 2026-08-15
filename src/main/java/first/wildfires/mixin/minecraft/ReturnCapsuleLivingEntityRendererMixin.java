/*
 * Adapted from NTM: Space RenderRocketCustom's reusable-rocket attitude transform.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: applies only the pod attitude at Forge 1.20.1's living-renderer
 * boundary; the dispatcher-level player mixin owns the rigid seat world position.
 */
package first.wildfires.mixin.minecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.client.space.ReturnCapsulePassengerPose;
import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a mounted player's rendered body inside the pitched reusable pod. */
@Mixin(LivingEntityRenderer.class)
public abstract class ReturnCapsuleLivingEntityRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void wildfires$alignReturnCapsulePassenger(LivingEntity entity, float entityYaw,
                                                        float partialTick, PoseStack poses,
                                                        MultiBufferSource buffers, int packedLight,
                                                        CallbackInfo ci) {
        if (entity.getVehicle() instanceof ReusableReturnCapsuleEntity capsule) {
            ReturnCapsulePassengerPose.applyAttitudeToPassenger(poses, capsule, partialTick);
        }
    }
}
