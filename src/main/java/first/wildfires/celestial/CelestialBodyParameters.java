package first.wildfires.celestial;

/** Immutable physical parameters for one orbiting body in the unified model. */
public record CelestialBodyParameters(double diameterKm,
                                      double orbitalDays,
                                      double semiMajorMillionKm,
                                      double synodicDays,
                                      double inclinationRadians) {

    public CelestialBodyParameters {
        if (!(diameterKm > 0.0D) || !(orbitalDays > 0.0D) || !(semiMajorMillionKm > 0.0D)
                || !(synodicDays > 0.0D) || !Double.isFinite(diameterKm)
                || !Double.isFinite(orbitalDays) || !Double.isFinite(semiMajorMillionKm)
                || !Double.isFinite(synodicDays) || !Double.isFinite(inclinationRadians)) {
            throw new IllegalArgumentException("Celestial body parameters must be finite and positive");
        }
    }
}
