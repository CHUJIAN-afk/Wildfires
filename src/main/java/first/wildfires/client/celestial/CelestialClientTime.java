package first.wildfires.client.celestial;

/** Converts local apparent day progress into the nonlinear angle expected by vanilla visual methods. */
public final class CelestialClientTime {

    private static final double MIDNIGHT = 0.75D;

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
        if (!Double.isFinite(solarEclipseCoverage) || solarEclipseCoverage <= 0.0D) {
            return solarApparentDayTime;
        }
        double coverage = Math.min(1.0D, solarEclipseCoverage);
        double midnightDistance = solarApparentDayTime - MIDNIGHT;
        midnightDistance -= Math.floor(midnightDistance + 0.5D);
        double visualTime = MIDNIGHT + midnightDistance * (1.0D - coverage);
        return visualTime - Math.floor(visualTime);
    }

    /** Converts the eclipse-adjusted visual time into the unit consumed by vanilla color methods. */
    public static float visualCelestialAngle(double solarApparentDayTime, double solarEclipseCoverage,
                                             float fallback) {
        return vanillaCelestialAngle(visualApparentDayTime(solarApparentDayTime, solarEclipseCoverage),
                fallback);
    }
}
