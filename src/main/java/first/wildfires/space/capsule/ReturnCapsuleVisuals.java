/*
 * Adapted from NTM: Space RenderDropPod and EntityRideableRocket reusable-capsule presentation.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: deterministic synchronized animation curves and bounded Forge particles.
 */
package first.wildfires.space.capsule;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import first.wildfires.register.SoundRegister;
import first.wildfires.space.content.SpaceContentRegister;
import first.wildfires.space.SpaceDimensions;
import net.minecraft.sounds.SoundSource;

/** Shared deterministic visual curves; real wall-clock time never drives capsule presentation. */
public final class ReturnCapsuleVisuals {

    private ReturnCapsuleVisuals() {
    }

    public static Snapshot snapshot(ReturnCapsuleState state, double phaseTicks,
                                    double verticalVelocity,
                                    boolean inOrbit) {
        double ticks = Math.max(0.0D, phaseTicks);
        float door = state == ReturnCapsuleState.SURFACE_LANDED
                ? (float) Mth.clamp(ticks * 2.0D, 0.0D, 90.0D)
                : state == ReturnCapsuleState.SURFACE_CLOSING
                ? (float) Mth.clamp(90.0D - ticks * 2.0D, 0.0D, 90.0D) : 0.0F;
        // NTM RenderDropPod opens the brakes only while LANDING motionY <= -0.4. Its original
        // wall-clock one-second close is expressed as 20 synchronized phase ticks here.
        boolean landing = state == ReturnCapsuleState.REENTRY
                || state == ReturnCapsuleState.SURFACE_LANDING;
        float brakes = landing && verticalVelocity <= -0.4D
                ? 65.0F : landing
                ? retractingAirbrakeDegrees(verticalVelocity) : 0.0F;
        // RenderDropPod bypasses every articulated transform and renderAll()s in orbit. The OBJ's
        // default leg position is therefore used for approach/dock/undock; only surface LANDED /
        // AWAITING / LANDING applies the legacy -0.5 extension translation.
        float legs = state == ReturnCapsuleState.SURFACE_LAUNCHING || inOrbit ? 0.0F : 1.0F;
        float pitch = state == ReturnCapsuleState.SURFACE_LAUNCHING
                ? (float) Mth.clamp((ticks - 60.0D) * 0.3D, 0.0D, 45.0D)
                : state == ReturnCapsuleState.SURFACE_TIPPING
                ? tippingPitchDegrees(ticks) : 0.0F;
        boolean mainEngine = state == ReturnCapsuleState.SURFACE_LAUNCHING
                || (landing && verticalVelocity > -0.4D);
        return new Snapshot(door, brakes, legs, pitch, mainEngine, inOrbit);
    }

    /** Test/source compatibility overload; live rendering uses synchronized NTM rocketVelocity. */
    public static Snapshot snapshot(ReturnCapsuleState state, double phaseTicks, Vec3 velocity,
                                    boolean inOrbit) {
        return snapshot(state, phaseTicks, velocity.y, inOrbit);
    }

    /** NTM's one-second wall-clock retraction expressed from its deterministic landing law. */
    static float retractingAirbrakeDegrees(double verticalVelocity) {
        if (verticalVelocity <= -0.4D) return 65.0F;
        if (verticalVelocity >= -0.005D) return 0.0F;
        double ticksSinceThreshold = Math.log(Math.abs(verticalVelocity) / 0.4D) / Math.log(0.99D);
        return (float) (65.0D * (1.0D - Mth.clamp(ticksSinceThreshold / 20.0D, 0.0D, 1.0D)));
    }

    /** Exact EntityRideableRocket TIPPING pitch: (stateTimer * 0.1)^2, capped at 90. */
    public static float tippingPitchDegrees(double phaseTicks) {
        double tipTime = Math.max(0.0D, phaseTicks) * 0.1D;
        return (float) Mth.clamp(tipTime * tipTime, 0.0D, 90.0D);
    }

    public static void spawnClientParticles(ReusableReturnCapsuleEntity capsule) {
        if (!capsule.level().isClientSide()) return;
        if (capsule.phaseTicks() <= 1) {
            if (capsule.capsuleState() == ReturnCapsuleState.SURFACE_LAUNCHING) {
                capsule.level().playLocalSound(capsule.getX(), capsule.getY(), capsule.getZ(),
                        SoundRegister.ReturnCapsuleIgnition.get(), SoundSource.PLAYERS,
                        1.0F, 1.0F, false);
            }
        }
        Snapshot visual = snapshot(capsule.capsuleState(), capsule.phaseTicks(),
                capsule.flightVelocity(), capsule.level().dimension().equals(SpaceDimensions.ORBIT));
        if (visual.mainEngine()) {
            double originX = capsule.getDeltaMovement().y > 0.0D ? capsule.xo : capsule.getX();
            double originY = capsule.getDeltaMovement().y > 0.0D ? capsule.yo : capsule.getY();
            double originZ = capsule.getDeltaMovement().y > 0.0D ? capsule.zo : capsule.getZ();
            for (int index = 0; index < 4; index++) {
                double angle = capsule.getYRot() * Mth.DEG_TO_RAD + index * Math.PI * 0.5D;
                double x = originX + Math.cos(angle) * 0.5D;
                double z = originZ + Math.sin(angle) * 0.5D;
                // NTM adds ParticleGasFlame directly to EffectRenderer. Use Level's forced,
                // always-visible path so the modern particle limiter/distance gate cannot discard
                // an exhaust state before its registered provider is asked to create it.
                capsule.level().addAlwaysVisibleParticle(
                        SpaceContentRegister.RETURN_CAPSULE_GAS_FLAME.get(), true,
                        x, originY, z, 0.0D, -1.0D, 0.0D);
            }
            // The client heightmap is allowed to return minBuildHeight while the destination
            // chunk is still applying after a dimension transfer (observed as Y=-64 beneath a
            // visible TFC surface at Y=-60). The authoritative landing top is synchronized on the
            // capsule, so NTM's ground shock remains attached to the real bound-body surface.
            double groundY = capsule.surfaceReferenceY().isPresent()
                    ? capsule.surfaceReferenceY().getAsInt()
                    : capsule.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types
                    .MOTION_BLOCKING_NO_LEAVES, Mth.floor(capsule.getX()), Mth.floor(capsule.getZ()));
            if (capsule.getY() - groundY < 10.0D) {
                // Exact ExplosionLarge.spawnShock + ClientProxy shock mode: one to three particle
                // states, a random integer-radian initial rotation, complete 1+Gaussian strength,
                // and equal angular spacing. Each particle renderer supplies its own six quads.
                int count = 1 + capsule.level().random.nextInt(3);
                double strength = 1.0D + capsule.level().random.nextGaussian();
                Vec3 direction = new Vec3(strength, 0.0D, 0.0D)
                        .yRot(capsule.level().random.nextInt(360));
                float step = Mth.TWO_PI / count;
                for (int index = 0; index < count; index++) {
                    capsule.level().addAlwaysVisibleParticle(
                            SpaceContentRegister.RETURN_CAPSULE_SHOCK_SMOKE.get(), true,
                            // Caller supplies NTM's groundHeight+0.5 and spawnShock adds another
                            // +0.5 before client creation. The previous half-height port buried
                            // most six-quad smoke geometry in the supporting floor.
                            capsule.getX(), groundY + 1.0D, capsule.getZ(),
                            direction.x, 0.0D, direction.z);
                    direction = direction.yRot(step);
                }
            }
        }
    }

    public record Snapshot(float doorDegrees, float airbrakeDegrees, float legExtension,
                           float pitchDegrees, boolean mainEngine, boolean orbitRenderAll) {
    }
}
