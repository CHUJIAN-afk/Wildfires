package first.wildfires.api.celestial;

/** Local solar information without changing vanilla's global time or light engine. */
public record DaylightState(double solarElevationRadians,
                            boolean sunAboveHorizon,
                            double apparentDayTime,
                            double daylightFactor) {
}
