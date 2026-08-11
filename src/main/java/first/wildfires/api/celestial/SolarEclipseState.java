package first.wildfires.api.celestial;

/** Immutable regional solar-eclipse state for the current observer and conjunction. */
public record SolarEclipseState(boolean activeSomewhere,
                                long conjunctionIndex,
                                double conjunctionCalendarTicks,
                                double trackLatitudeRadians,
                                double greatestTrackLatitudeRadians,
                                double globalCoverage,
                                double localMaximumCoverage,
                                SolarEclipseZone zone) {

    public static final SolarEclipseState NONE = new SolarEclipseState(false, 0L, 0.0D,
            0.0D, 0.0D, 0.0D, 0.0D, SolarEclipseZone.NONE);

    public SolarEclipseState {
        if (zone == null) {
            throw new IllegalArgumentException("Solar eclipse zone cannot be null");
        }
    }
}
