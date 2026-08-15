/*
 * NTM: Space supplies the reusable-rocket transition baseline; Wildfires generalizes it from
 * fixed absolute Y levels to bound-body-relative, size-aware and atmosphere-aware distances.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package first.wildfires.space.celestial;

import first.wildfires.celestial.CelestialBodies;
import first.wildfires.celestial.CelestialPlanetSettings;
import net.minecraft.resources.ResourceLocation;

/** Immutable surface-flight distances for one celestial body, expressed above its landing top. */
public record CelestialTransferProfile(
        double planetHalfSize,
        double revealStartAltitude,
        double revealEndAltitude,
        double atmosphereFadeStartAltitude,
        double atmosphereFadeEndAltitude,
        double reentryAltitude,
        double transferAltitude,
        double perspectiveNumerator) {

    public static final double EARTH_DIAMETER_KM = 12_742.0D;
    public static final double MOON_DIAMETER_KM = 3_474.8D;
    private static final ResourceLocation EARTH = id("earth");
    private static final ResourceLocation MOON = id("moon");

    public static CelestialTransferProfile resolve(ResourceLocation bodyId,
                                                    CelestialVisualDefinition visual,
                                                    CelestialPlanetSettings settings) {
        double diameter = diameterKm(bodyId, settings);
        // Fourth-root scaling preserves a real and monotonic size effect without making a
        // Jupiter-class body require an eleven-times-long Minecraft flight corridor.
        double sizeScale = clamp(Math.pow(diameter / EARTH_DIAMETER_KM, 0.25D), 0.55D, 2.20D);
        CelestialVisualDefinition.Atmosphere atmosphere = visual.atmosphere();
        double atmosphereScale;
        if (atmosphere.enabled()) {
            double shellScale = Math.sqrt(Math.max(1.0E-4D,
                    (atmosphere.radiusMultiplier() - 1.0D) / 0.025D));
            double densityScale = Math.pow(Math.max(0.05D, atmosphere.density()), 0.10D);
            atmosphereScale = clamp(sizeScale * clamp(shellScale, 0.55D, 2.0D)
                    * clamp(densityScale, 0.75D, 1.40D), 0.45D, 2.80D);
        } else {
            atmosphereScale = clamp(sizeScale * 0.55D, 0.35D, 1.40D);
        }

        double revealStart = 200.0D * sizeScale;
        double revealEnd = revealStart + 300.0D * sizeScale;
        double fadeStart = Math.max(revealStart, 300.0D * atmosphereScale);
        double fadeEnd = Math.max(revealEnd, 800.0D * atmosphereScale);
        double corridorScale = Math.max(sizeScale, atmosphereScale);
        double reentry = Math.max(fadeEnd, 800.0D * corridorScale);
        double transfer = Math.max(reentry + 100.0D, 900.0D * corridorScale);
        return new CelestialTransferProfile(100.0D * sizeScale, revealStart, revealEnd,
                fadeStart, fadeEnd, reentry, transfer, 1_150.0D * sizeScale);
    }

    public double curvature(double altitude) {
        if (!Double.isFinite(altitude)) return 1.0D;
        return clamp((atmosphereFadeEndAltitude - altitude)
                / (atmosphereFadeEndAltitude - atmosphereFadeStartAltitude), 0.0D, 1.0D);
    }

    public double revealAlpha(double altitude) {
        if (!Double.isFinite(altitude)) return 0.0D;
        return clamp((altitude - revealStartAltitude)
                / (revealEndAltitude - revealStartAltitude), 0.0D, 1.0D);
    }

    private static double diameterKm(ResourceLocation bodyId, CelestialPlanetSettings settings) {
        if (EARTH.equals(bodyId)) return settings.earthDiameterKm();
        if (MOON.equals(bodyId)) return MOON_DIAMETER_KM;
        CelestialBodies known = CelestialBodies.byId(bodyId);
        return known == null ? settings.earthDiameterKm() : settings.parameters(known).diameterKm();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("wildfires", path);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
