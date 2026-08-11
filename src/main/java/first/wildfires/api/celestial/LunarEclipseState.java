package first.wildfires.api.celestial;

/**
 * Immutable projection of the terrestrial square shadow onto the rendered lunar pixel disc.
 * Umbra and penumbra coverage are produced by the same authoritative geometry used by rendering.
 */
public record LunarEclipseState(long fullMoonIndex,
                                double fullMoonCalendarTicks,
                                double lunarLatitudeRadians,
                                double effectiveLatitudeRadians,
                                double shadowCenterX,
                                double shadowCenterY,
                                double shadowRadius,
                                double umbraCoverage,
                                double penumbraCoverage) {

    public static final LunarEclipseState NONE = new LunarEclipseState(0L, 0.0D,
            0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

    public LunarEclipseState {
        if (!Double.isFinite(fullMoonCalendarTicks)
                || !Double.isFinite(lunarLatitudeRadians)
                || !Double.isFinite(effectiveLatitudeRadians)
                || !Double.isFinite(shadowCenterX) || !Double.isFinite(shadowCenterY)
                || !Double.isFinite(shadowRadius) || shadowRadius < 0.0D
                || !unitInterval(umbraCoverage) || !unitInterval(penumbraCoverage)) {
            throw new IllegalArgumentException("Lunar eclipse projection must be finite and normalized");
        }
    }

    /** Includes penumbral-only eclipses from first positive-area contact. */
    public boolean active() {
        return penumbraCoverage > 0.0D;
    }

    public boolean penumbralOnly() {
        return active() && umbraCoverage == 0.0D;
    }

    private static boolean unitInterval(double value) {
        return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
    }
}
