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
                             double physicalSolarEclipse,
                             SolarEclipseState solarEclipseRegion,
                             double lunarEclipse,
                             LunarEclipseState lunarEclipseRegion,
                             double supermoon,
                             double bloodMoon,
                             double sunScale,
                             double moonScale,
                             double weatherVisibility,
                             DaylightState daylight) {

    /** A lunar eclipse becomes a blood moon only above eighty percent square-disc coverage. */
    public static final double BLOOD_MOON_COVERAGE_THRESHOLD = 0.8D;

    /** Near-perigee strength required in addition to the full-moon window. */
    public static final double SUPERMOON_STRENGTH_THRESHOLD = 0.98D;

    public CelestialState {
        orbitingBodies = List.copyOf(orbitingBodies);
        if (solarEclipseRegion == null || lunarEclipseRegion == null) {
            throw new IllegalArgumentException("Eclipse region states cannot be null");
        }
        if (!Double.isFinite(sunScale) || sunScale <= 0.0D
                || !Double.isFinite(moonScale) || moonScale <= 0.0D) {
            throw new IllegalArgumentException("Celestial visual scales must be finite and positive");
        }
    }

    public boolean localDay() {
        return sun.altitudeRadians() > 0.0D;
    }

    public boolean localNight() {
        return sun.altitudeRadians() <= 0.0D;
    }

    public boolean moonAboveHorizon() {
        return moon.altitudeRadians() > 0.0D;
    }

    /** Local solar events are valid only while the Sun is above this latitude's horizon. */
    public boolean visibleSolarEclipse() {
        return solarEclipse > 0.0D && localDay();
    }

    /** Local lunar events require both a visible Moon and local night. */
    public boolean visibleLunarEclipse() {
        return lunarEclipseRegion.active() && moonAboveHorizon() && localNight();
    }

    public boolean visibleSupermoon() {
        return supermoon >= SUPERMOON_STRENGTH_THRESHOLD && moonAboveHorizon() && localNight();
    }

    public boolean visibleBloodMoon() {
        return bloodMoon > BLOOD_MOON_COVERAGE_THRESHOLD && moonAboveHorizon() && localNight();
    }
}
