package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.api.celestial.LunarEclipseState;

/**
 * Stable per-full-Moon terrestrial-shadow projection. The physical lunar orbit retains its real
 * inclination; only the shadow's cross-track displacement is stylized to keep enlarged pixel discs
 * from turning nearly every full Moon into a weak grazing eclipse.
 */
public final class LunarEclipseRegion {

    public static final double CENTER_LATITUDE_MULTIPLIER = 0.25D;
    public static final double OUTER_LATITUDE_MULTIPLIER = 2.65D;
    public static final double INNER_TRANSITION_LATITUDE = 1.1D * CelestialMath.DEG_TO_RAD;
    public static final double OUTER_TRANSITION_LATITUDE = 1.9D * CelestialMath.DEG_TO_RAD;
    public static final double ANNUAL_OPPORTUNITY_EXPONENT = 1.2D;
    public static final double REFERENCE_YEAR_DAYS = 8.0D * CelestialMath.MONTHS_IN_YEAR;

    private LunarEclipseRegion() {
    }

    public static Event eventFor(double calendarDays, double daysInYear, double synodicDays,
                                 double nodalYears, double lunarInclination) {
        if (!positiveFinite(synodicDays)) {
            return Event.NONE;
        }
        long index = Math.round(calendarDays / synodicDays);
        return eventAt(index, daysInYear, synodicDays, nodalYears, lunarInclination);
    }

    static Event eventAt(long fullMoonIndex, double daysInYear, double synodicDays,
                         double nodalYears, double lunarInclination) {
        if (!positiveFinite(daysInYear) || !positiveFinite(synodicDays)
                || !positiveFinite(nodalYears) || !Double.isFinite(lunarInclination)) {
            return Event.NONE;
        }
        double fullMoonDay = fullMoonIndex * synodicDays;
        double fractionOfYear = CelestialMath.positiveModulo(fullMoonDay, daysInYear) / daysInYear;
        double solarLongitude = CelestialMath.TAU
                * CelestialMath.positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        double moonLongitude = solarLongitude + Math.PI;
        double ascendingNode = CelestialMath.lunarAscendingNode(fullMoonDay, daysInYear, nodalYears);
        double lunarLatitude = Math.asin(Math.sin(lunarInclination)
                * Math.sin(moonLongitude - ascendingNode));
        double effectiveLatitude = effectiveLatitudeRadians(lunarLatitude, daysInYear, synodicDays);
        if (!Double.isFinite(lunarLatitude) || !Double.isFinite(effectiveLatitude)) {
            return Event.NONE;
        }
        return new Event(fullMoonIndex, fullMoonDay, lunarLatitude, effectiveLatitude, true);
    }

    /**
     * Odd, monotone and C1-continuous stylized shadow latitude. Near a node the smaller scale keeps
     * the surviving eclipse deep; away from it the rapidly increasing scale moves weak grazes clear
     * of even the penumbra. This changes only the terrestrial shadow, never the physical Moon.
     */
    static double effectiveLatitudeRadians(double lunarLatitude) {
        return effectiveLatitudeRadians(lunarLatitude, REFERENCE_YEAR_DAYS,
                CelestialMath.SYNODIC_DAYS);
    }

    /** Keeps eclipses-per-calendar-year stable when month length or lunar period is configured. */
    static double effectiveLatitudeRadians(double lunarLatitude, double daysInYear,
                                           double synodicDays) {
        if (!Double.isFinite(lunarLatitude)) {
            return Double.NaN;
        }
        double absoluteLatitude = Math.abs(lunarLatitude);
        double blend = smoothstep(INNER_TRANSITION_LATITUDE, OUTER_TRANSITION_LATITUDE,
                absoluteLatitude);
        double multiplier = CENTER_LATITUDE_MULTIPLIER
                + (OUTER_LATITUDE_MULTIPLIER - CENTER_LATITUDE_MULTIPLIER) * blend;
        double referenceOpportunities = REFERENCE_YEAR_DAYS / CelestialMath.SYNODIC_DAYS;
        double annualOpportunities = positiveFinite(daysInYear) && positiveFinite(synodicDays)
                ? daysInYear / synodicDays : referenceOpportunities;
        double opportunityScale = Math.pow(annualOpportunities / referenceOpportunities,
                ANNUAL_OPPORTUNITY_EXPONENT);
        double maximumProjectionLatitude = Math.PI * 0.5D - 1.0E-6D;
        return clamp(lunarLatitude * multiplier * opportunityScale,
                -maximumProjectionLatitude, maximumProjectionLatitude);
    }

    public static Projection project(Event event, CelestialVector moonDirection,
                                     CelestialVector sunDirection, CelestialVector celestialNorth,
                                     double moonHalfTangent, double shadowHalfTangent) {
        if (event == null || !event.valid() || !positiveFinite(moonHalfTangent)
                || !positiveFinite(shadowHalfTangent)) {
            return Projection.NONE;
        }
        CelestialDiscGeometry.AlignedSquare raw = CelestialDiscGeometry.alignedSquareProjection(
                moonDirection, sunDirection == null ? null : sunDirection.negated(), celestialNorth,
                moonHalfTangent, shadowHalfTangent);
        if (!raw.valid()) {
            return Projection.NONE;
        }

        // At exact full Moon raw centerY is -tan(lunarLatitude)/moonHalfTangent. Add only the
        // stable event latitude delta, preserving the raw orbit's continuous vertical drift.
        double additionalProjection = Math.tan(event.effectiveLatitudeRadians())
                - Math.tan(event.lunarLatitudeRadians());
        double centerY = raw.centerY() - additionalProjection / moonHalfTangent;
        if (!Double.isFinite(centerY)) {
            return Projection.NONE;
        }
        CelestialDiscGeometry.AlignedSquare shadow = new CelestialDiscGeometry.AlignedSquare(
                raw.centerX(), centerY, raw.radius(), true);
        double umbra = CelestialDiscGeometry.alignedSquareCoverage(shadow);
        CelestialDiscGeometry.AlignedSquare expanded = new CelestialDiscGeometry.AlignedSquare(
                shadow.centerX(), shadow.centerY(),
                shadow.radius() + CelestialDiscGeometry.LUNAR_PENUMBRA_NORMALIZED_WIDTH, true);
        double penumbra = CelestialDiscGeometry.alignedSquareCoverage(expanded);
        LunarEclipseState state = new LunarEclipseState(event.fullMoonIndex(),
                event.fullMoonDay() * CelestialMath.TICKS_IN_DAY,
                event.lunarLatitudeRadians(), event.effectiveLatitudeRadians(),
                shadow.centerX(), shadow.centerY(), shadow.radius(), umbra, penumbra);
        return new Projection(shadow, state);
    }

    private static boolean positiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double x = clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    public record Event(long fullMoonIndex, double fullMoonDay,
                        double lunarLatitudeRadians, double effectiveLatitudeRadians,
                        boolean valid) {
        public static final Event NONE = new Event(0L, 0.0D, 0.0D,
                0.0D, false);
    }

    public record Projection(CelestialDiscGeometry.AlignedSquare shadow,
                             LunarEclipseState state) {
        public static final Projection NONE = new Projection(
                CelestialDiscGeometry.AlignedSquare.NONE, LunarEclipseState.NONE);

        public double umbraCoverage() {
            return state.umbraCoverage();
        }

        public double penumbraCoverage() {
            return state.penumbraCoverage();
        }
    }
}
