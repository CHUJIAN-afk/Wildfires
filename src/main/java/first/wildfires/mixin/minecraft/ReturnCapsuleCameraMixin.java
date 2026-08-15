/*
 * Adapted from NTM: Space RenderRocketCustom's reusable-rocket attitude transform.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: applies the full attitude to first person and anchors third person to
 * the renderer-domain capsule seat before vanilla performs its collision-clamped zoom.
 */
package first.wildfires.mixin.minecraft;

import first.wildfires.client.space.ReturnCapsulePassengerPose;
import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps both camera modes on the reusable pod without rotating the detached camera into it. */
@Mixin(Camera.class)
public abstract class ReturnCapsuleCameraMixin {

    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"
            )
    )
    private void wildfires$anchorDetachedCameraToReturnCapsule(BlockGetter level, Entity entity,
                                                                boolean detached,
                                                                boolean thirdPersonReverse,
                                                                float partialTick,
                                                                CallbackInfo callback) {
        if (!detached || !(entity.getVehicle() instanceof ReusableReturnCapsuleEntity capsule)
                || !capsule.hasPassenger(entity)) return;

        // LevelRenderer draws the pod from xOld/yOld/zOld. Anchor the detached camera to that
        // exact renderer-domain seat before Camera#getMaxZoom casts its vanilla collision rays.
        // Only the centre is translated: player look direction, front/rear view and the completed
        // zoom remain vanilla, and the pod attitude is deliberately not applied a second time.
        Vec3 playerCameraAnchor = ReturnCapsulePassengerPose.interpolatedPosition(
                entity, partialTick);
        Vec3 capsuleRenderSeat = ReturnCapsulePassengerPose.interpolatedRenderSeatPosition(
                capsule, partialTick);
        Vec3 current = ((Camera) (Object) this).getPosition();
        setPosition(current.add(capsuleRenderSeat.subtract(playerCameraAnchor)));
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void wildfires$followReturnCapsuleAttitude(BlockGetter level, Entity entity,
                                                        boolean detached,
                                                        boolean thirdPersonReverse,
                                                        float partialTick, CallbackInfo callback) {
        // Vanilla has already collision-clamped a detached camera. Rotating that finished offset
        // here moves third person back through the capsule and directly into the fresh exhaust
        // billboards (the bright, apparently stuck particle seen in the 1407 counterexample).
        if (detached) return;
        if (!(entity.getVehicle() instanceof ReusableReturnCapsuleEntity capsule)) return;
        Quaternionf attitude = ReturnCapsulePassengerPose.attitudeQuaternion(capsule, partialTick);
        Vec3 base = ReturnCapsulePassengerPose.interpolatedPosition(entity, partialTick);
        Vec3 seat = ReturnCapsulePassengerPose.interpolatedSeatPosition(capsule, partialTick);
        Vec3 current = ((Camera) (Object) this).getPosition();
        Vector3f offset = new Vector3f((float) (current.x - base.x),
                (float) (current.y - base.y), (float) (current.z - base.z));
        attitude.transform(offset);
        setPosition(seat.add(offset.x, offset.y, offset.z));
        rotation.premul(attitude).normalize();
        forwards.set(0.0F, 0.0F, 1.0F).rotate(rotation);
        up.set(0.0F, 1.0F, 0.0F).rotate(rotation);
        left.set(1.0F, 0.0F, 0.0F).rotate(rotation);
    }
}
