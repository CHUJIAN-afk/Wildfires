package first.wildfires.api.celestial;

import java.util.List;

/** Immutable snapshot shared by rendering, gameplay and compatibility consumers. */
public record CelestialState(double latitudeRadians,
                             double fractionOfDay,
                             double fractionOfYear,
                             long calendarTicks,
                             CelestialBodyState sun,
                             CelestialBodyState moon,
                             CelestialVector celestialNorth,
                             List<CelestialBodyState> orbitingBodies,
                             int moonPhase,
                             double solarEclipse,
                             double lunarEclipse,
                             double supermoon,
                             double bloodMoon,
                             double weatherVisibility,
                             DaylightState daylight) {

    public CelestialState {
        orbitingBodies = List.copyOf(orbitingBodies);
    }
}
