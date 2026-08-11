package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.api.celestial.SolarEclipseState;
import first.wildfires.api.celestial.SolarEclipseZone;

/**
 * Latitude-only lunar-shadow model for the TFC/TFE repeating hemisphere grid.
 * X deliberately has no longitude semantics, so a moving latitude track supplies the only
 * regional dimension while the rendered square Sun and Moon remain the coverage authority.
 */
public final class SolarEclipseRegion {

    public static final double PARTIAL_HALF_WIDTH = Math.toRadians(24.0D);
    public static final double LATITUDE_RADIANS_PER_LUNAR_RADIAN = 28.0D;
    public static final double TRACK_DRIFT_RADIANS_PER_DAY = Math.toRadians(12.0D);
    private static final double MODEL_WINDOW_MULTIPLIER = 1.5D;
    private static final double MODEL_BLEND_START_MULTIPLIER = 1.25D;
    private static final int MAXIMUM_SAMPLES = 64;
    private static final int LATITUDE_EDGE_ITERATIONS = 48;
    private static final int GLOBAL_MAXIMUM_ITERATIONS = 32;
    private static final CelestialVector CANONICAL_SUN = new CelestialVector(0.0D, 0.0D, 1.0D);
    private static final CelestialVector CANONICAL_NORTH = new CelestialVector(0.0D, 1.0D, 0.0D);

    private SolarEclipseRegion() {
    }

    public static Event eventFor(double calendarDays, double daysInYear, double synodicDays,
                                 double nodalYears, double lunarInclination) {
        if (!positiveFinite(synodicDays)) {
            return Event.NONE;
        }
        long index = Math.round(calendarDays / synodicDays - 0.5D);
        return eventAt(index, daysInYear, synodicDays, nodalYears, lunarInclination);
    }

    public static Event eventAt(long conjunctionIndex, double daysInYear, double synodicDays,
                                double nodalYears, double lunarInclination) {
        if (!positiveFinite(daysInYear) || !positiveFinite(synodicDays)
                || !positiveFinite(nodalYears) || !Double.isFinite(lunarInclination)) {
            return Event.NONE;
        }
        double conjunctionDay = (conjunctionIndex + 0.5D) * synodicDays;
        double fractionOfYear = CelestialMath.positiveModulo(conjunctionDay, daysInYear) / daysInYear;
        double solarLongitude = CelestialMath.TAU
                * CelestialMath.positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        double ascendingNode = CelestialMath.lunarAscendingNode(conjunctionDay, daysInYear, nodalYears);
        double lunarLatitude = Math.asin(Math.sin(lunarInclination)
                * Math.sin(solarLongitude - ascendingNode));
        double greatestLatitude = -lunarLatitude * LATITUDE_RADIANS_PER_LUNAR_RADIAN;
        double maximumTrackExcursion = TRACK_DRIFT_RADIANS_PER_DAY * 0.35D;
        boolean intersectsWorld = Math.abs(greatestLatitude)
                <= Math.PI * 0.5D + PARTIAL_HALF_WIDTH + maximumTrackExcursion;
        return new Event(conjunctionIndex, conjunctionDay, daysInYear, lunarLatitude, greatestLatitude,
                intersectsWorld, true);
    }

    public static Projection project(Event event, double calendarDays, double observerLatitude,
                                     CelestialVector sunDirection, CelestialVector rawMoonDirection,
                                     CelestialVector celestialNorth, double sunHalfTangent,
                                     double moonHalfTangent, double synodicDays) {
        if (event == null || !event.valid()
                || !positiveFinite(sunHalfTangent) || !positiveFinite(moonHalfTangent)
                || !positiveFinite(synodicDays) || !Double.isFinite(observerLatitude)) {
            return Projection.none(rawMoonDirection);
        }
        double contact = sunHalfTangent + moonHalfTangent;
        double dayOffset = calendarDays - event.conjunctionDay();
        double phaseOffset = CelestialMath.TAU * dayOffset / synodicDays;
        if (Math.abs(phaseOffset) >= Math.PI * 0.5D) {
            return Projection.none(rawMoonDirection);
        }
        // stableBasis.right() points opposite to increasing lunar elongation in the
        // horizon projection. Keep the regional track on the same branch as the raw
        // three-dimensional Moon so blending cannot fold the apparent path back across
        // the Sun and manufacture a second eclipse contact window.
        double alongTrack = -Math.tan(phaseOffset);
        double modelWindow = contact * MODEL_WINDOW_MULTIPLIER;
        double trackLatitude = event.greatestLatitude() + TRACK_DRIFT_RADIANS_PER_DAY * dayOffset;

        CelestialDiscGeometry.Basis basis = CelestialDiscGeometry.stableBasis(sunDirection, celestialNorth);
        CelestialVector sun = sunDirection.normalized();
        CelestialVector rawMoon = rawMoonDirection.normalized();
        double forward = rawMoon.dot(sun);
        if (!Double.isFinite(forward) || forward <= 1.0E-9D) {
            return Projection.none(rawMoonDirection);
        }
        double rawX = rawMoon.dot(basis.right()) / forward;
        double rawY = rawMoon.dot(basis.up()) / forward;
        double blendDistance = Math.min(Math.abs(alongTrack), Math.abs(rawX));
        if (blendDistance >= modelWindow) {
            return Projection.none(rawMoonDirection);
        }
        double modeledCrossTrack = (observerLatitude - trackLatitude) * contact / PARTIAL_HALF_WIDTH;
        // Do not begin returning to the raw orbit until both the modeled and raw paths
        // are outside the square-pixel contact radius. Their same-side interpolation can
        // then no longer cross the Sun while the authority reports zero coverage.
        double blendStart = contact * MODEL_BLEND_START_MULTIPLIER;
        double modelWeight = 1.0D - smoothstep(blendStart, modelWindow, blendDistance);
        double x = lerp(rawX, alongTrack, modelWeight);
        double y = lerp(rawY, modeledCrossTrack, modelWeight);
        CelestialVector apparentMoon = sun.add(basis.right().scale(x)).add(basis.up().scale(y)).normalized();
        double coverage = CelestialDiscGeometry.squareCoverage(sun, apparentMoon, celestialNorth,
                sunHalfTangent, moonHalfTangent);
        double globalCoverage = maximumCoverageAtTime(event, calendarDays, sunHalfTangent,
                moonHalfTangent, synodicDays);
        globalCoverage = Math.max(globalCoverage, coverage);
        if (!(globalCoverage > 0.0D)) {
            return new Projection(apparentMoon, 0.0D, SolarEclipseState.NONE);
        }
        double maximum = maximumCoverageAtLatitude(event, observerLatitude, sunHalfTangent,
                moonHalfTangent, synodicDays);
        SolarEclipseState state = new SolarEclipseState(true, event.conjunctionIndex(),
                event.conjunctionDay() * CelestialMath.TICKS_IN_DAY, trackLatitude,
                event.greatestLatitude(), globalCoverage, maximum,
                SolarEclipseZone.fromMaximumCoverage(maximum));
        return new Projection(apparentMoon, coverage, state);
    }

    /** Coverage at one latitude and one instant in the regional square-pixel shadow model. */
    public static double coverageAt(Event event, double calendarDays, double observerLatitude,
                                    double sunHalfTangent, double moonHalfTangent,
                                    double synodicDays) {
        if (event == null || !event.valid() || !event.intersectsWorld()
                || !Double.isFinite(calendarDays) || !Double.isFinite(observerLatitude)
                || !positiveFinite(sunHalfTangent) || !positiveFinite(moonHalfTangent)
                || !positiveFinite(synodicDays)) {
            return 0.0D;
        }
        double phaseOffset = CelestialMath.TAU * (calendarDays - event.conjunctionDay()) / synodicDays;
        if (Math.abs(phaseOffset) >= Math.PI * 0.5D) {
            return 0.0D;
        }
        double contact = sunHalfTangent + moonHalfTangent;
        double alongTrack = Math.tan(phaseOffset);
        double trackLatitude = event.trackLatitude(calendarDays);
        double crossTrack = (observerLatitude - trackLatitude) * contact / PARTIAL_HALF_WIDTH;
        return projectedSquareCoverage(sunHalfTangent, moonHalfTangent, -alongTrack, crossTrack,
                solarNorthDot(calendarDays, event.daysInYear()));
    }

    /** Maximum coverage across the complete effective-latitude world at one instant. */
    public static double maximumCoverageAtTime(Event event, double calendarDays,
                                               double sunHalfTangent, double moonHalfTangent,
                                               double synodicDays) {
        if (event == null || !event.valid() || !event.intersectsWorld()) {
            return 0.0D;
        }
        double track = event.trackLatitude(calendarDays);
        double searchRadius = PARTIAL_HALF_WIDTH * MODEL_WINDOW_MULTIPLIER;
        double low = clamp(track - searchRadius, -Math.PI * 0.5D, Math.PI * 0.5D);
        double high = clamp(track + searchRadius, -Math.PI * 0.5D, Math.PI * 0.5D);
        if (!(high >= low)) {
            return 0.0D;
        }
        double maximum = Math.max(
                coverageAt(event, calendarDays, low, sunHalfTangent, moonHalfTangent, synodicDays),
                coverageAt(event, calendarDays, high, sunHalfTangent, moonHalfTangent, synodicDays));
        for (int iteration = 0; iteration < GLOBAL_MAXIMUM_ITERATIONS; iteration++) {
            double firstThird = (2.0D * low + high) / 3.0D;
            double secondThird = (low + 2.0D * high) / 3.0D;
            double firstCoverage = coverageAt(event, calendarDays, firstThird,
                    sunHalfTangent, moonHalfTangent, synodicDays);
            double secondCoverage = coverageAt(event, calendarDays, secondThird,
                    sunHalfTangent, moonHalfTangent, synodicDays);
            maximum = Math.max(maximum, Math.max(firstCoverage, secondCoverage));
            if (firstCoverage < secondCoverage) {
                low = firstThird;
            } else {
                high = secondThird;
            }
        }
        double middle = (low + high) * 0.5D;
        return Math.max(maximum, coverageAt(event, calendarDays, middle,
                sunHalfTangent, moonHalfTangent, synodicDays));
    }

    /** Latitude half-width reaching at least the requested coverage at one instant. */
    public static double latitudeHalfWidthAt(Event event, double calendarDays,
                                             double sunHalfTangent, double moonHalfTangent,
                                             double synodicDays, double coverageThreshold) {
        if (event == null || !event.valid() || !event.intersectsWorld()
                || !Double.isFinite(calendarDays) || !positiveFinite(sunHalfTangent)
                || !positiveFinite(moonHalfTangent) || !positiveFinite(synodicDays)
                || !Double.isFinite(coverageThreshold) || coverageThreshold < 0.0D
                || coverageThreshold > 1.0D) {
            return 0.0D;
        }
        double phaseOffset = CelestialMath.TAU * (calendarDays - event.conjunctionDay()) / synodicDays;
        if (Math.abs(phaseOffset) >= Math.PI * 0.5D) {
            return 0.0D;
        }
        double contact = sunHalfTangent + moonHalfTangent;
        double centerX = -Math.tan(phaseOffset);
        double northDot = solarNorthDot(calendarDays, event.daysInYear());
        double centerCoverage = projectedSquareCoverage(sunHalfTangent, moonHalfTangent,
                centerX, 0.0D, northDot);
        if ((coverageThreshold <= 0.0D && !(centerCoverage > 0.0D))
                || (coverageThreshold > 0.0D && centerCoverage < coverageThreshold)) {
            return 0.0D;
        }
        double low = 0.0D;
        double high = contact * MODEL_WINDOW_MULTIPLIER;
        for (int iteration = 0; iteration < LATITUDE_EDGE_ITERATIONS; iteration++) {
            double middle = (low + high) * 0.5D;
            double coverage = projectedSquareCoverage(sunHalfTangent, moonHalfTangent,
                    centerX, middle, northDot);
            boolean inside = coverageThreshold <= 0.0D
                    ? coverage > 0.0D : coverage >= coverageThreshold;
            if (inside) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return clamp(low / contact * PARTIAL_HALF_WIDTH, 0.0D, PARTIAL_HALF_WIDTH);
    }

    /** Maximum square-pixel coverage at one effective latitude during this conjunction. */
    public static double maximumCoverageAtLatitude(Event event, double observerLatitude,
                                                   double sunHalfTangent, double moonHalfTangent,
                                                   double synodicDays) {
        if (event == null || !event.valid() || !event.intersectsWorld()
                || !Double.isFinite(observerLatitude) || !positiveFinite(sunHalfTangent)
                || !positiveFinite(moonHalfTangent) || !positiveFinite(synodicDays)) {
            return 0.0D;
        }
        double contact = sunHalfTangent + moonHalfTangent;
        double halfDuration = Math.atan(contact) * synodicDays / CelestialMath.TAU;
        double maximum = 0.0D;
        for (int sample = 0; sample <= MAXIMUM_SAMPLES; sample++) {
            double dayOffset = (sample / (double) MAXIMUM_SAMPLES * 2.0D - 1.0D) * halfDuration;
            maximum = Math.max(maximum, coverageAt(event, event.conjunctionDay() + dayOffset,
                    observerLatitude, sunHalfTangent, moonHalfTangent, synodicDays));
        }
        return clamp(maximum, 0.0D, 1.0D);
    }

    /** Exact perspective coverage for the same celestial-north square bases used by rendering. */
    static double projectedSquareCoverage(double firstHalf, double secondHalf,
                                          double centerX, double centerY, double northDot) {
        if (!positiveFinite(firstHalf) || !positiveFinite(secondHalf)
                || !Double.isFinite(centerX) || !Double.isFinite(centerY)
                || !Double.isFinite(northDot)) {
            return 0.0D;
        }
        CelestialVector moon = new CelestialVector(centerX, centerY, 1.0D).normalized();
        double clampedNorthDot = clamp(northDot, -1.0D, 1.0D);
        CelestialVector north = new CelestialVector(0.0D,
                Math.sqrt(Math.max(0.0D, 1.0D - clampedNorthDot * clampedNorthDot)),
                clampedNorthDot);
        return CelestialDiscGeometry.squareCoverage(CANONICAL_SUN, moon, north,
                firstHalf, secondHalf);
    }

    private static double solarNorthDot(double calendarDays, double daysInYear) {
        if (!Double.isFinite(calendarDays) || !positiveFinite(daysInYear)) {
            return 0.0D;
        }
        double fractionOfYear = CelestialMath.positiveModulo(calendarDays, daysInYear) / daysInYear;
        double solarLongitude = CelestialMath.TAU
                * CelestialMath.positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        double declination = CelestialMath.AXIAL_TILT * Math.sin(solarLongitude);
        return Math.sin(declination);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (!(edge1 > edge0)) {
            return value <= edge0 ? 0.0D : 1.0D;
        }
        double x = clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    private static double lerp(double first, double second, double amount) {
        return first + (second - first) * amount;
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Event(long conjunctionIndex, double conjunctionDay, double daysInYear,
                        double lunarLatitude, double greatestLatitude,
                        boolean intersectsWorld, boolean valid) {
        public static final Event NONE = new Event(0L, 0.0D, 0.0D,
                0.0D, 0.0D, false, false);

        public double trackLatitude(double calendarDays) {
            return greatestLatitude + TRACK_DRIFT_RADIANS_PER_DAY * (calendarDays - conjunctionDay);
        }
    }

    public record Projection(CelestialVector moonDirection, double coverage, SolarEclipseState state) {
        static Projection none(CelestialVector moonDirection) {
            return new Projection(moonDirection, 0.0D, SolarEclipseState.NONE);
        }
    }
}
