package first.wildfires.client.celestial;

/** Converts local apparent day progress into the nonlinear angle expected by vanilla visual methods. */
public final class CelestialClientTime {

    private static final double MIDNIGHT = 0.75D;
    public static final double ECLIPSE_DARKENING_START = 0.20D;

    private CelestialClientTime() {
    }

    /**
     * Matches {@code DimensionType.timeOfDay(long)} for an apparent vanilla day time expressed in {@code [0, 1)}.
     * Sky, cloud, star and fog methods consume this angle, not the raw tick fraction.
     */
    public static float vanillaCelestialAngle(double apparentDayTime, float fallback) {
        if (!Double.isFinite(apparentDayTime)) {
            return fallback;
        }
        double rawPhase = apparentDayTime - Math.floor(apparentDayTime) - 0.25D;
        double phase = rawPhase - Math.floor(rawPhase);
        double eased = 0.5D - Math.cos(phase * Math.PI) * 0.5D;
        return (float) (phase * 2.0D + eased) / 3.0F;
    }

    /**
     * Reproduces TFCCaelum's eclipse-driven visual darkening with the unified geometric coverage.
     * This is deliberately a client-only derived time: the authoritative daylight state and the
     * physical Sun direction remain unchanged.
     */
    public static double visualApparentDayTime(double solarApparentDayTime, double solarEclipseCoverage) {
        if (!Double.isFinite(solarApparentDayTime)) {
            return solarApparentDayTime;
        }
        double coverage = eclipseVisualIntensity(solarEclipseCoverage);
        if (coverage <= 0.0D) {
            return solarApparentDayTime;
        }
        double midnightDistance = solarApparentDayTime - MIDNIGHT;
        midnightDistance -= Math.floor(midnightDistance + 0.5D);
        double visualTime = MIDNIGHT + midnightDistance * (1.0D - coverage);
        return visualTime - Math.floor(visualTime);
    }

    /** Linear visual response: first 20% is ordinary daylight, then 20..100% maps to 0..1. */
    public static double eclipseVisualIntensity(double solarEclipseCoverage) {
        if (!Double.isFinite(solarEclipseCoverage)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D,
                (solarEclipseCoverage - ECLIPSE_DARKENING_START) / (1.0D - ECLIPSE_DARKENING_START)));
    }

    /** Converts the eclipse-adjusted visual time into the unit consumed by vanilla color methods. */
    public static float visualCelestialAngle(double solarApparentDayTime, double solarEclipseCoverage,
                                             float fallback) {
        return vanillaCelestialAngle(visualApparentDayTime(solarApparentDayTime, solarEclipseCoverage),
                fallback);
    }
}
