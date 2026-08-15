/*
 * Adapted from NTM: Space RenderRocketCustom's reusable-rocket attitude transform.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: shares the Forge 1.20.1 pod attitude and the exact renderer/camera
 * interpolation domains with its mounted passenger.
 */
package first.wildfires.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.capsule.ReturnCapsuleVisuals;
import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** One shared render-space attitude for the pod mesh and its mounted passenger body. */
public final class ReturnCapsulePassengerPose {

    private ReturnCapsulePassengerPose() {
    }

    public static void applyAttitudeToPassenger(PoseStack poses,
                                                ReusableReturnCapsuleEntity capsule,
                                                float partialTick) {
        float yaw = interpolatedYaw(capsule, partialTick);
        float pitch = interpolatedPitch(capsule, partialTick);
        applyAttitude(poses, yaw, pitch);
    }

    /** Camera.setup uses xo/yo/zo; keep its seat in that exact interpolation domain. */
    public static Vec3 interpolatedSeatPosition(ReusableReturnCapsuleEntity capsule,
                                                 float partialTick) {
        Vec3 capsulePosition = interpolatedPosition(capsule, partialTick);
        return seatPosition(capsule, capsulePosition, partialTick);
    }

    /** LevelRenderer.renderEntity uses xOld/yOld/zOld, not Camera's xo/yo/zo fields. */
    public static Vec3 interpolatedRenderPosition(Entity entity, float partialTick) {
        return new Vec3(Mth.lerp((double) partialTick, entity.xOld, entity.getX()),
                Mth.lerp((double) partialTick, entity.yOld, entity.getY()),
                Mth.lerp((double) partialTick, entity.zOld, entity.getZ()));
    }

    /** Rigid seat anchor in the exact interpolation domain used by EntityRenderDispatcher. */
    public static Vec3 interpolatedRenderSeatPosition(ReusableReturnCapsuleEntity capsule,
                                                       float partialTick) {
        return seatPosition(capsule, interpolatedRenderPosition(capsule, partialTick), partialTick);
    }

    /** Complete dispatcher-level player offset; Forge render events inherit the same rigid anchor. */
    public static Vec3 passengerRenderOffset(Entity passenger,
                                             ReusableReturnCapsuleEntity capsule,
                                             float partialTick) {
        return interpolatedRenderSeatPosition(capsule, partialTick)
                .subtract(interpolatedRenderPosition(passenger, partialTick));
    }

    private static Vec3 seatPosition(ReusableReturnCapsuleEntity capsule, Vec3 capsulePosition,
                                     float partialTick) {
        float yaw = interpolatedYaw(capsule, partialTick);
        float pitch = interpolatedPitch(capsule, partialTick);
        double pitchRadians = Math.toRadians(pitch - 90.0F);
        double yawRadians = Math.toRadians(180.0F - yaw);
        double length = ReusableReturnCapsuleEntity.CAPSULE_SEAT_OFFSET;
        return capsulePosition.add(-Math.sin(yawRadians) * Math.cos(pitchRadians) * length,
                -Math.sin(pitchRadians) * length,
                Math.cos(yawRadians) * Math.cos(pitchRadians) * length);
    }

    public static Vec3 interpolatedPosition(Entity entity, float partialTick) {
        return new Vec3(Mth.lerp((double) partialTick, entity.xo, entity.getX()),
                Mth.lerp((double) partialTick, entity.yo, entity.getY()),
                Mth.lerp((double) partialTick, entity.zo, entity.getZ()));
    }

    /** Exact NTM RenderRocketCustom conjugation; order is part of the visual contract. */
    static void applyAttitude(PoseStack poses, float yaw, float pitch) {
        float yawAroundPitch = yaw - 90.0F;
        poses.mulPose(Axis.YP.rotationDegrees(yawAroundPitch));
        poses.mulPose(Axis.ZP.rotationDegrees(pitch));
        poses.mulPose(Axis.YP.rotationDegrees(-yawAroundPitch));
    }

    /** World-space quaternion shared with Camera.setup so first person cannot remain upright. */
    public static Quaternionf attitudeQuaternion(ReusableReturnCapsuleEntity capsule,
                                                  float partialTick) {
        float yaw = interpolatedYaw(capsule, partialTick);
        float pitch = interpolatedPitch(capsule, partialTick);
        float yawAroundPitch = (yaw - 90.0F) * Mth.DEG_TO_RAD;
        return new Quaternionf().rotationY(yawAroundPitch)
                .rotateZ(pitch * Mth.DEG_TO_RAD)
                .rotateY(-yawAroundPitch);
    }

    private static float interpolatedYaw(ReusableReturnCapsuleEntity capsule, float partialTick) {
        return Mth.rotLerp(partialTick, capsule.yRotO, capsule.getYRot());
    }

    private static float interpolatedPitch(ReusableReturnCapsuleEntity capsule,
                                           float partialTick) {
        boolean inOrbit = capsule.level().dimension().equals(SpaceDimensions.ORBIT);
        return ReturnCapsuleVisuals.snapshot(capsule.capsuleState(),
                capsule.phaseTicks() + partialTick, capsule.flightVelocity(), inOrbit)
                .pitchDegrees();
    }
}
