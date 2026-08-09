package first.wildfires.client.celestial;

import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.celestial.CelestialEventRules;

/** Pure client-visual decisions shared by renderers and deterministic acceptance tests. */
final class CelestialVisualRules {

    private static final double DEG_TO_RAD = Math.PI / 180.0D;
    private static final double TFCCAELUM_ARCSECONDS_PER_RADIAN = 206265.0D;
    private static final double SKY_SPHERE_RADIUS = 100.0D;
    static final double SATELLITE_ORBIT_RENDER_SCALE = 50.0D;
    static final long RAINBOW_DURATION_TICKS = 5000L;
    private static final double LEGACY_RAINBOW_CURVE = 0.0003544658838845707D;

    private CelestialVisualRules() {
    }

    static double auroraProbability(double absoluteLatitudeDegrees) {
        return CelestialEventRules.auroraProbability(absoluteLatitudeDegrees);
    }

    static boolean auroraVisible(boolean legacyGlobal, boolean disabled, int bands,
                                 double latitudeRadians, double sunAltitudeRadians, double deterministicRoll) {
        return CelestialEventRules.auroraVisible(legacyGlobal, disabled, bands, latitudeRadians,
                sunAltitudeRadians, deterministicRoll);
    }

    static double auroraNightFactor(double sunAltitudeRadians) {
        return smoothstep(-6.0D * DEG_TO_RAD, -18.0D * DEG_TO_RAD, sunAltitudeRadians);
    }

    static double twilightAlpha(double sunAltitudeRadians, double weatherVisibility) {
        if (!Double.isFinite(sunAltitudeRadians) || !Double.isFinite(weatherVisibility)) {
            return 0.0D;
        }
        double alpha = 1.0D - Math.abs(sunAltitudeRadians + DEG_TO_RAD) / (11.0D * DEG_TO_RAD);
        return clamp(alpha, 0.0D, 1.0D) * clamp(weatherVisibility, 0.0D, 1.0D);
    }

    /** Matches ClientLevel.getStarBrightness for a local apparent vanilla day time. */
    static double vanillaStarAlpha(double apparentDayTime) {
        if (!Double.isFinite(apparentDayTime)) {
            return 0.0D;
        }
        double celestialAngle = CelestialClientTime.vanillaCelestialAngle(apparentDayTime, Float.NaN);
        if (!Double.isFinite(celestialAngle)) {
            return 0.0D;
        }
        double alpha = 1.0D - (Math.cos(celestialAngle * Math.PI * 2.0D) * 2.0D + 0.25D);
        alpha = clamp(alpha, 0.0D, 1.0D);
        return alpha * alpha * 0.5D;
    }

    static double starVisibility(double apparentDayTime, double weatherVisibility) {
        if (!Double.isFinite(weatherVisibility)) {
            return 0.0D;
        }
        return vanillaStarAlpha(apparentDayTime) * clamp(weatherVisibility, 0.0D, 1.0D);
    }

    /** Caelum's logarithmic magnitude intent, made finite at negative catalog magnitudes. */
    static StarAppearance starAppearance(double magnitude, double catalogMinMagnitude,
                                         double catalogMaxMagnitude, double globalScale) {
        if (!Double.isFinite(magnitude) || !Double.isFinite(catalogMinMagnitude)
                || !Double.isFinite(catalogMaxMagnitude) || catalogMaxMagnitude < catalogMinMagnitude
                || !Double.isFinite(globalScale) || globalScale <= 0.0D) {
            return StarAppearance.HIDDEN;
        }
        double boundedMagnitude = clamp(magnitude, catalogMinMagnitude, catalogMaxMagnitude);
        double shift = catalogMinMagnitude <= 0.0D ? 1.0D - catalogMinMagnitude : 0.0D;
        double shiftedMin = catalogMinMagnitude + shift;
        double shiftedMax = catalogMaxMagnitude + shift;
        double shiftedMagnitude = boundedMagnitude + shift;
        double logMin = Math.log10(shiftedMin);
        double logMax = Math.log10(shiftedMax);
        double normalized = logMax > logMin
                ? clamp((Math.log10(shiftedMagnitude) - logMin) / (logMax - logMin), 0.0D, 1.0D)
                : 0.0D;
        return new StarAppearance((0.6D - normalized * 0.5D) * globalScale, 1.0D - normalized);
    }

    /** Caelum applies configured brightness once to the whole star buffer, after local night and weather. */
    static double starShaderBrightness(double apparentDayTime, double weatherVisibility,
                                       double configuredBrightness) {
        if (!Double.isFinite(configuredBrightness) || configuredBrightness <= 0.0D) {
            return 0.0D;
        }
        return starVisibility(apparentDayTime, weatherVisibility) * configuredBrightness;
    }

    /** TFC 1.21 keeps the daytime moon at 20% prominence and restores it continuously at night. */
    static double moonVisibility(double apparentDayTime, double weatherVisibility) {
        if (!Double.isFinite(apparentDayTime) || !Double.isFinite(weatherVisibility)) {
            return 0.0D;
        }
        return (0.2D + 0.8D * vanillaStarAlpha(apparentDayTime))
                * clamp(weatherVisibility, 0.0D, 1.0D);
    }

    /** The vanilla new-moon cell is an opaque dark square; a physical new moon has no ordinary visible face. */
    static boolean moonTextureVisible(int moonPhase) {
        return moonPhase >= 0 && moonPhase < 8 && moonPhase != 4;
    }

    /** Every valid lunar phase, including the invisible new moon, must occult visible background stars. */
    static boolean moonSkyCoverVisible(int moonPhase, double apparentDayTime, double weatherVisibility) {
        return moonPhase >= 0 && moonPhase < 8
                && starVisibility(apparentDayTime, weatherVisibility) > 0.001D;
    }

    /** Vanilla's solid lunar pixels occupy the centered 8x8 area (12..19) of each 32x32 phase cell. */
    static double moonAtlasBodyHalfSize(double moonQuadRadius) {
        return Double.isFinite(moonQuadRadius) && moonQuadRadius > 0.0D
                ? moonQuadRadius * 4.0D / 16.0D
                : 0.0D;
    }

    /** The low-brightness circular gradient occupies pixels 5..26 around the centered lunar body. */
    static double moonAtlasGlowRadius(double moonQuadRadius) {
        return Double.isFinite(moonQuadRadius) && moonQuadRadius > 0.0D
                ? moonQuadRadius * 11.0D / 16.0D
                : 0.0D;
    }

    /** Phase-dependent visual halo that attenuates nearby stars without changing the star catalog. */
    static MoonHalo moonHalo(double illuminatedFraction, double weatherVisibility) {
        if (!Double.isFinite(illuminatedFraction) || !Double.isFinite(weatherVisibility)) {
            return MoonHalo.NONE;
        }
        double phase = clamp(illuminatedFraction, 0.0D, 1.0D);
        double weather = clamp(weatherVisibility, 0.0D, 1.0D);
        if (phase <= 0.0D || weather <= 0.0D) {
            return MoonHalo.NONE;
        }
        double strength = Math.pow(phase, 0.75D) * weather;
        return new MoonHalo(1.5D + 2.0D * Math.sqrt(phase), 0.34D * strength);
    }

    static double moonHaloAlpha(MoonHalo halo, double normalizedRadius) {
        if (halo == null || !Double.isFinite(normalizedRadius)) {
            return 0.0D;
        }
        return halo.centerAlpha() * clamp(1.0D - normalizedRadius, 0.0D, 1.0D);
    }

    static boolean discVisible(double altitudeRadians) {
        return Double.isFinite(altitudeRadians) && altitudeRadians > -3.0D * DEG_TO_RAD;
    }

    static double planetVisibility(double altitudeRadians, double apparentDayTime, double weatherVisibility) {
        return Double.isFinite(altitudeRadians) && altitudeRadians > 0.0D
                ? starVisibility(apparentDayTime, weatherVisibility) : 0.0D;
    }

    /**
     * Restores TFCCaelum's exact small-angle apparent-diameter quad scale. The unified state stores
     * atan2(diameter / 2, distance), so twice its tangent reconstructs the source diameter/distance
     * ratio before applying TFCCaelum's fixed 206265 arcseconds-per-radian approximation. Every
     * planet and satellite therefore retains the source ratio and its own scale factor; the global
     * setting remains a single linear multiplier for the whole system.
     */
    static double planetRenderRadius(double angularRadiusRadians, double bodyScale, double globalScale) {
        if (!Double.isFinite(angularRadiusRadians) || angularRadiusRadians <= 0.0D
                || !Double.isFinite(bodyScale) || bodyScale <= 0.0D
                || !Double.isFinite(globalScale) || globalScale <= 0.0D) {
            return 0.0D;
        }
        double apparentDiameterArcSeconds = 2.0D * Math.tan(angularRadiusRadians)
                * TFCCAELUM_ARCSECONDS_PER_RADIAN;
        double radius = apparentDiameterArcSeconds * bodyScale * globalScale;
        return Double.isFinite(radius) ? radius : 0.0D;
    }

    /**
     * Restores TFCCaelum's visual-only x50 satellite separation on the unified three-dimensional
     * tangent direction. The physical API position, distance and angular size stay untouched.
     */
    static CelestialVector satelliteRenderDirection(CelestialVector parentDirection,
                                                     CelestialVector satelliteDirection) {
        CelestialVector parent = parentDirection.normalized();
        CelestialVector satellite = satelliteDirection.normalized();
        if (parent.lengthSquared() < 1.0E-12D || satellite.lengthSquared() < 1.0E-12D) {
            return satellite;
        }
        double dot = clamp(parent.dot(satellite), -1.0D, 1.0D);
        CelestialVector tangent = satellite.subtract(parent.scale(dot));
        double tangentLength = tangent.length();
        if (tangentLength < 1.0E-12D) {
            return satellite;
        }
        double physicalAngle = Math.atan2(tangentLength, dot);
        double visualAngle = Math.min(Math.PI * 0.5D, physicalAngle * SATELLITE_ORBIT_RENDER_SCALE);
        CelestialVector tangentDirection = tangent.scale(1.0D / tangentLength);
        return parent.scale(Math.cos(visualAngle))
                .add(tangentDirection.scale(Math.sin(visualAngle))).normalized();
    }

    /**
     * Builds a celestial-north-locked tangent frame for textured sky discs. A world-up billboard
     * has an unavoidable hard reference-axis switch around the zenith; projecting celestial north
     * keeps the Sun, Moon and planet textures continuous along the ecliptic instead.
     */
    static DiscBasis stableDiscBasis(CelestialVector bodyDirection, CelestialVector celestialNorth) {
        CelestialVector direction = finiteUnit(bodyDirection, new CelestialVector(0.0D, 1.0D, 0.0D));
        CelestialVector north = finiteUnit(celestialNorth, new CelestialVector(0.0D, 0.0D, 1.0D));
        CelestialVector up = north.subtract(direction.scale(direction.dot(north)));
        if (up.lengthSquared() < 1.0E-12D) {
            CelestialVector fallback = leastAlignedAxis(direction);
            up = fallback.subtract(direction.scale(direction.dot(fallback)));
        }
        up = finiteUnit(up, new CelestialVector(0.0D, 0.0D, 1.0D));
        CelestialVector right = finiteUnit(cross(up, direction), new CelestialVector(1.0D, 0.0D, 0.0D));
        up = finiteUnit(cross(direction, right), up);
        return new DiscBasis(right, up);
    }

    static boolean startsRainbow(float rainBefore, float currentRain, float rainAfter, double apparentDayTime,
                                 double sunAltitudeRadians) {
        return CelestialEventRules.startsRainbow(rainBefore, currentRain, rainAfter,
                apparentDayTime, sunAltitudeRadians);
    }

    static boolean rainbowVisible(long remainingTicks, double apparentDayTime, double sunAltitudeRadians) {
        return remainingTicks > 0L
                && apparentDayTime < 13000.0D / 24000.0D
                && sunAltitudeRadians > 0.0D;
    }

    /** Unit direction opposite the Sun with a stable fallback at the solar zenith/nadir singularity. */
    static RainbowDirection rainbowDirection(double sunX, double sunZ) {
        double horizontalLength = Math.sqrt(sunX * sunX + sunZ * sunZ);
        double x;
        double z;
        if (!Double.isFinite(horizontalLength) || horizontalLength < 1.0E-9D) {
            x = 0.0D;
            z = 1.0D;
        } else {
            x = -sunX / horizontalLength;
            z = -sunZ / horizontalLength;
        }
        double length = Math.sqrt(x * x + 0.25D * 0.25D + z * z);
        return new RainbowDirection(x / length, 0.25D / length, z / length);
    }

    /** TFCCaelum's 5000-tick rainbow curve, with its empirical eclipse term replaced by disc coverage. */
    static double rainbowAlpha(long remainingTicks, double solarEclipseCoverage) {
        double timer = clamp(remainingTicks, 0L, RAINBOW_DURATION_TICKS);
        double curve = Math.sin(Math.pow(timer * LEGACY_RAINBOW_CURVE, 2.0D));
        double eclipseVisibility = 1.0D - clamp(Double.isFinite(solarEclipseCoverage)
                ? solarEclipseCoverage : 0.0D, 0.0D, 1.0D);
        return clamp(curve * eclipseVisibility, 0.0D, 1.0D);
    }

    static long advanceRainbowTimer(long remainingTicks, long elapsedTicks, boolean visibleWindow) {
        long remaining = Math.max(0L, Math.min(RAINBOW_DURATION_TICKS, remainingTicks));
        if (!visibleWindow || elapsedTicks <= 0L) {
            return remaining;
        }
        return elapsedTicks >= remaining ? 0L : remaining - elapsedTicks;
    }

    static int solarEclipseFrame(double coverage) {
        double obscured = clamp(Double.isFinite(coverage) ? coverage : 0.0D, 0.0D, 1.0D);
        return Math.min(7, Math.max(0, (int) Math.round((1.0D - obscured) * 7.0D)));
    }

    /** Bounded equivalent of TFCCaelum's eclipse Sun shader color using unified geometric coverage. */
    static SunTint solarEclipseSunTint(double coverage) {
        double obscured = clamp(Double.isFinite(coverage) ? coverage : 0.0D, 0.0D, 1.0D);
        double remaining = 1.0D - obscured;
        return new SunTint(1.0D, remaining, remaining);
    }

    static MoonTint bloodMoonTint(double intensity) {
        double value = clamp(Double.isFinite(intensity) ? intensity : 0.0D, 0.0D, 1.0D);
        return new MoonTint(1.0D + value * 0.25D, 1.0D - value * 0.675D,
                1.0D - value * 0.85D);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double x = clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static CelestialVector finiteUnit(CelestialVector vector, CelestialVector fallback) {
        if (vector != null && Double.isFinite(vector.x()) && Double.isFinite(vector.y())
                && Double.isFinite(vector.z()) && vector.lengthSquared() > 1.0E-12D) {
            return vector.normalized();
        }
        return fallback;
    }

    private static CelestialVector leastAlignedAxis(CelestialVector direction) {
        double x = Math.abs(direction.x());
        double y = Math.abs(direction.y());
        double z = Math.abs(direction.z());
        if (x <= y && x <= z) {
            return new CelestialVector(1.0D, 0.0D, 0.0D);
        }
        if (y <= z) {
            return new CelestialVector(0.0D, 1.0D, 0.0D);
        }
        return new CelestialVector(0.0D, 0.0D, 1.0D);
    }

    private static CelestialVector cross(CelestialVector first, CelestialVector second) {
        return new CelestialVector(first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
    }

    record MoonTint(double red, double green, double blue) {}

    record SunTint(double red, double green, double blue) {}

    record RainbowDirection(double x, double y, double z) {}

    record DiscBasis(CelestialVector right, CelestialVector up) {}

    record MoonHalo(double radiusMultiplier, double centerAlpha) {
        private static final MoonHalo NONE = new MoonHalo(0.0D, 0.0D);
    }

    record StarAppearance(double radius, double alpha) {
        private static final StarAppearance HIDDEN = new StarAppearance(0.0D, 0.0D);
    }
}
