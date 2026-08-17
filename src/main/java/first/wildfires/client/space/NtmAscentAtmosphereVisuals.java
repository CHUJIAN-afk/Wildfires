/*
 * Adapted from NTM: Space SkyProviderCelestial.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires changes: scopes the source atmosphere and underfoot-body altitude curves to an
 * explicitly bound reusable-return-capsule surface ascents, gates them to a directly seated player,
 * and exposes them to Forge 1.20.1.
 */
package first.wildfires.client.space;

import first.wildfires.celestial.CelestialSettingsCache;
import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import first.wildfires.space.capsule.ReturnCapsuleState;
import first.wildfires.space.celestial.CelestialDefinition;
import first.wildfires.space.celestial.CelestialDefinitionRegistry;
import first.wildfires.space.celestial.CelestialKind;
import first.wildfires.space.celestial.CelestialTransferProfile;
import first.wildfires.space.celestial.CelestialVisualDefinition;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/** NTM's surface-to-vacuum colour curve for a reusable capsule on any explicitly bound body. */
public final class NtmAscentAtmosphereVisuals {

    public static final double FADE_START_Y = 300.0D;
    public static final double FADE_END_Y = 800.0D;
    public static final double PLANET_REVEAL_START_Y = 200.0D;
    public static final double PLANET_REVEAL_END_Y = 500.0D;
    public static final double PLANET_HALF_SIZE = 100.0D;
    private static final double NTM_PLANET_HALF_TANGENT_NUMERATOR = 1_150.0D;
    private static final double SURFACE_EXIT_EPSILON = 1.0E-5D;

    private NtmAscentAtmosphereVisuals() {
    }

    /** Exact NTM {@code clamp((800 - Y) / 500, 0, 1)} curvature term. */
    public static double curvature(double altitude) {
        if (!Double.isFinite(altitude)) {
            return 1.0D;
        }
        return Mth.clamp((FADE_END_Y - altitude) / (FADE_END_Y - FADE_START_Y),
                0.0D, 1.0D);
    }

    public static float factor(ClientLevel level, Camera camera) {
        AscentFrame frame = frame(level, camera).orElse(null);
        return frame == null ? 1.0F : (float) frame.profile().curvature(frame.altitude());
    }

    /** NTM's clamped {@code (Y - 200) / 300} opacity for the surface below the rocket. */
    public static double planetAlpha(double altitude) {
        if (!Double.isFinite(altitude)) {
            return 0.0D;
        }
        return Mth.clamp((altitude - PLANET_REVEAL_START_Y)
                / (PLANET_REVEAL_END_Y - PLANET_REVEAL_START_Y), 0.0D, 1.0D);
    }

    public static float planetAlpha(ClientLevel level, Camera camera) {
        AscentFrame frame = frame(level, camera).orElse(null);
        return frame == null ? 0.0F : (float) frame.profile().revealAlpha(frame.altitude());
    }

    /** Distance from the cube centre to the outermost shell along the camera's world-up ray. */
    public static double outerShellExitDistance(Quaternionf rotation, double radiusMultiplier) {
        return outerShellExitDistance(rotation, radiusMultiplier, PLANET_HALF_SIZE);
    }

    public static double outerShellExitDistance(Quaternionf rotation, double radiusMultiplier,
                                                double planetHalfSize) {
        Vector3f localUp = new Vector3f(0.0F, 1.0F, 0.0F);
        new Quaternionf(rotation).conjugate().transform(localUp);
        double maximumComponent = Math.max(Math.abs(localUp.x),
                Math.max(Math.abs(localUp.y), Math.abs(localUp.z)));
        if (!Double.isFinite(maximumComponent) || maximumComponent < 1.0E-7D
                || !Double.isFinite(radiusMultiplier) || radiusMultiplier < 1.0D) {
            return planetHalfSize;
        }
        return planetHalfSize * radiusMultiplier / maximumComponent;
    }

    /** Includes the surface, rigid orbital-cloud shell and atmosphere shell in the exit boundary. */
    public static double outerShellRadiusMultiplier(CelestialVisualDefinition visual) {
        double multiplier = 1.0D;
        if (visual.clouds().enabled()) {
            multiplier = Math.max(multiplier, visual.clouds().radiusMultiplier());
            if (visual.clouds().shadowStrength() > 0.001D) multiplier = Math.max(multiplier, 1.001D);
        }
        if (visual.atmosphere().enabled()) {
            multiplier = Math.max(multiplier, visual.atmosphere().radiusMultiplier());
        }
        return multiplier;
    }

    /**
     * The reveal begins with the camera exactly on the rotated outer shell at Y=200. Subsequent
     * altitude increases retain NTM's {@code 1150/Y} apparent-size rate while moving strictly
     * outside the real cube instead of assuming an unrotated half-size.
     */
    public static double planetCenterDistance(double altitude, double outerShellExitDistance) {
        return planetCenterDistance(altitude, outerShellExitDistance, PLANET_REVEAL_START_Y,
                PLANET_HALF_SIZE, NTM_PLANET_HALF_TANGENT_NUMERATOR);
    }

    public static double planetCenterDistance(double altitude, double outerShellExitDistance,
                                              CelestialTransferProfile profile) {
        return planetCenterDistance(altitude, outerShellExitDistance,
                profile.revealStartAltitude(), profile.planetHalfSize(),
                profile.perspectiveNumerator());
    }

    private static double planetCenterDistance(double altitude, double outerShellExitDistance,
                                               double revealStart, double planetHalfSize,
                                               double perspectiveNumerator) {
        if (!Double.isFinite(outerShellExitDistance) || outerShellExitDistance < planetHalfSize) {
            outerShellExitDistance = planetHalfSize;
        }
        if (!Double.isFinite(altitude)) return outerShellExitDistance;
        double clearanceAltitude = Math.max(0.0D, altitude - revealStart);
        return outerShellExitDistance
                + planetHalfSize * clearanceAltitude / perspectiveNumerator;
    }

    /** Alpha is zero until the camera is geometrically outside every rendered planet shell. */
    public static double planetAlpha(double altitude, double centerDistance,
                                     double outerShellExitDistance) {
        if (!Double.isFinite(centerDistance) || !Double.isFinite(outerShellExitDistance)
                || centerDistance <= outerShellExitDistance + SURFACE_EXIT_EPSILON) return 0.0D;
        return planetAlpha(altitude);
    }

    public static double planetAlpha(double altitude, double centerDistance,
                                     double outerShellExitDistance,
                                     CelestialTransferProfile profile) {
        if (!Double.isFinite(centerDistance) || !Double.isFinite(outerShellExitDistance)
                || centerDistance <= outerShellExitDistance + SURFACE_EXIT_EPSILON) return 0.0D;
        return profile.revealAlpha(altitude);
    }

    public static boolean hasLeftRenderedSurface(double altitude) {
        return Double.isFinite(altitude) && altitude > PLANET_REVEAL_START_Y;
    }

    /** Size-aware form used by every local surface-scene gate. */
    public static boolean hasLeftRenderedSurface(double altitude,
                                                 CelestialTransferProfile profile) {
        return profile != null && Double.isFinite(altitude)
                && altitude > profile.revealStartAltitude();
    }

    /** Cancels only Minecraft/TFC's local cloud deck; the planet's orbital cloud shell is separate. */
    public static boolean hideLocalClouds(ClientLevel level, Camera camera) {
        AscentFrame frame = frame(level, camera).orElse(null);
        return frame != null && hasLeftRenderedSurface(frame.altitude(), frame.profile());
    }

    /** Rain/snow belongs to the local surface scene and cannot continue over the revealed cube. */
    public static boolean hideLocalWeather(ClientLevel level, Camera camera) {
        return hideLocalClouds(level, camera);
    }

    /** The loaded overworld chunks are a local flat surface, not a second planet inside the cube. */
    public static boolean hideLocalTerrain(ClientLevel level, Camera camera) {
        AscentFrame frame = frame(level, camera).orElse(null);
        return frame != null && hasLeftRenderedSurface(frame.altitude(), frame.profile());
    }

    public static Vec3 fadeSky(ClientLevel level, Camera camera, Vec3 color) {
        float factor = factor(level, camera);
        return factor >= 1.0F ? color : color.scale(factor);
    }

    public static Optional<ResourceLocation> ascentBody(ClientLevel level, Camera camera) {
        ReusableReturnCapsuleEntity capsule = playerCapsule(camera);
        if (level == null || capsule == null) return Optional.empty();
        ReturnCapsuleState state = capsule.capsuleState();
        if (state != ReturnCapsuleState.SURFACE_LAUNCHING
                && state != ReturnCapsuleState.ASCENT_TRANSITION
                && state != ReturnCapsuleState.REENTRY
                && state != ReturnCapsuleState.SURFACE_LANDING) return Optional.empty();
        ResourceLocation bodyId = capsule.activeBodyId().orElse(null);
        if (bodyId == null) return Optional.empty();
        CelestialDefinition definition = CelestialDefinitionRegistry.get(level.registryAccess()).get(bodyId);
        if (definition == null || definition.kind() == CelestialKind.STAR
                || definition.surfaceDimension().filter(level.dimension().location()::equals).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(bodyId);
    }

    /** One resolved body-relative frame shared by square-body, sky and local-scene gates. */
    public static Optional<AscentFrame> frame(ClientLevel level, Camera camera) {
        ReusableReturnCapsuleEntity capsule = playerCapsule(camera);
        if (level == null || capsule == null) return Optional.empty();
        ResourceLocation bodyId = ascentBody(level, camera).orElse(null);
        if (bodyId == null) return Optional.empty();
        CelestialDefinition definition = CelestialDefinitionRegistry.get(level.registryAccess()).get(bodyId);
        if (definition == null) return Optional.empty();
        int surfaceY = capsule.surfaceReferenceY().orElse(0);
        CelestialTransferProfile profile = CelestialTransferProfile.resolve(bodyId,
                definition.visual(), CelestialSettingsCache.current().planetSettings());
        return Optional.of(new AscentFrame(bodyId, definition, profile,
                camera.getPosition().y - surfaceY));
    }

    private static ReusableReturnCapsuleEntity playerCapsule(Camera camera) {
        if (camera == null) return null;
        Entity observer = camera.getEntity();
        if (!(observer instanceof Player)
                || !(observer.getVehicle() instanceof ReusableReturnCapsuleEntity capsule)
                || capsule.getFirstPassenger() != observer) return null;
        return capsule;
    }

    private static boolean active(ClientLevel level, Camera camera) {
        return ascentBody(level, camera).isPresent();
    }

    public record AscentFrame(ResourceLocation bodyId, CelestialDefinition definition,
                              CelestialTransferProfile profile, double altitude) {
    }
}
