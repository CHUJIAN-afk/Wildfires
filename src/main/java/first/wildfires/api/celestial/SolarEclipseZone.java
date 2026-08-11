package first.wildfires.api.celestial;

/** Classification of a location by the maximum solar coverage reached during one eclipse. */
public enum SolarEclipseZone {
    NONE,
    PARTIAL,
    PENUMBRA,
    UMBRA;

    public static SolarEclipseZone fromMaximumCoverage(double coverage) {
        if (!Double.isFinite(coverage) || coverage <= 0.0D) {
            return NONE;
        }
        if (coverage >= 0.8D) {
            return UMBRA;
        }
        return coverage >= 0.5D ? PENUMBRA : PARTIAL;
    }
}
