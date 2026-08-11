package first.wildfires.api.celestial;

/**
 * Immutable, local Sun/Moon event view derived from one authoritative celestial snapshot.
 * It is query-only and does not modify world time, lighting, sleep or machine rules.
 */
public record CelestialEventState(boolean localDay,
                                  boolean localNight,
                                  boolean sunAboveHorizon,
                                  boolean moonAboveHorizon,
                                  boolean fullMoon,
                                  boolean newMoon,
                                  boolean firstQuarter,
                                  boolean lastQuarter,
                                  double solarEclipseCoverage,
                                  boolean solarEclipseVisible,
                                  double lunarEclipseCoverage,
                                  double lunarPenumbraCoverage,
                                  boolean lunarEclipseVisible,
                                  double supermoonStrength,
                                  boolean supermoonVisible,
                                  double bloodMoonStrength,
                                  boolean bloodMoonVisible) {

    public static CelestialEventState from(CelestialState state) {
        double illumination = state.moon().illuminatedFraction();
        boolean full = illumination >= 0.995D;
        boolean fresh = illumination <= 0.005D;
        boolean first = state.moonPhase() == 2 && Math.abs(illumination - 0.5D) <= 0.03D;
        boolean last = state.moonPhase() == 6 && Math.abs(illumination - 0.5D) <= 0.03D;
        boolean localNight = state.localNight();
        boolean moonVisibleAtNight = state.moonAboveHorizon() && localNight;
        return new CelestialEventState(state.localDay(), state.localNight(),
                state.sun().altitudeRadians() > 0.0D, state.moonAboveHorizon(),
                full && moonVisibleAtNight,
                fresh && state.moonAboveHorizon() && state.localDay(),
                first && moonVisibleAtNight, last && moonVisibleAtNight,
                state.solarEclipse(), state.visibleSolarEclipse(),
                state.lunarEclipse(), state.lunarEclipseRegion().penumbraCoverage(),
                state.visibleLunarEclipse(),
                state.supermoon(), state.visibleSupermoon(),
                state.bloodMoon(), state.visibleBloodMoon());
    }
}
