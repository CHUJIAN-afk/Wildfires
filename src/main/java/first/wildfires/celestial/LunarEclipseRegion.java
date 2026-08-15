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
    private static final ThreadLocal<OpportunityScaleCache> OPPORTUNITY_SCALE_CACHE =
            ThreadLocal.withInitial(OpportunityScaleCache::new);
    private static final ThreadLocal<EventCache> PREPARED_EVENT_CACHE =
            ThreadLocal.withInitial(EventCache::new);
    private static final ThreadLocal<ProjectionDeltaCache> PROJECTION_DELTA_CACHE =
            ThreadLocal.withInitial(ProjectionDeltaCache::new);
    private static final ThreadLocal<CoverageOutput> PROJECTION_OUTPUT =
            ThreadLocal.withInitial(CoverageOutput::new);

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

    static Event eventFor(double calendarDays, double daysInYear, double synodicDays,
                          double nodalYears, double lunarInclination,
                          double sineLunarInclination) {
        if (!positiveFinite(synodicDays)) {
            return Event.NONE;
        }
        long index = Math.round(calendarDays / synodicDays);
        return eventAt(index, daysInYear, synodicDays, nodalYears, lunarInclination,
                sineLunarInclination);
    }

    /** Internal repeated-sample path; public event factories retain their per-call object result. */
    static Event eventForPrepared(double calendarDays, double daysInYear, double synodicDays,
                                  double nodalYears, double lunarInclination,
                                  double sineLunarInclination) {
        if (!positiveFinite(synodicDays)) {
            return Event.NONE;
        }
        long index = Math.round(calendarDays / synodicDays);
        return PREPARED_EVENT_CACHE.get().get(index, daysInYear, synodicDays, nodalYears,
                lunarInclination, sineLunarInclination);
    }

    static Event eventAt(long fullMoonIndex, double daysInYear, double synodicDays,
                         double nodalYears, double lunarInclination) {
        return eventAt(fullMoonIndex, daysInYear, synodicDays, nodalYears,
                lunarInclination, Math.sin(lunarInclination));
    }

    static Event eventAt(long fullMoonIndex, double daysInYear, double synodicDays,
                         double nodalYears, double lunarInclination,
                         double sineLunarInclination) {
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
        double lunarLatitude = Math.asin(sineLunarInclination
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
        double opportunityScale = OPPORTUNITY_SCALE_CACHE.get().get(daysInYear, synodicDays);
        double maximumProjectionLatitude = Math.PI * 0.5D - 1.0E-6D;
        return clamp(lunarLatitude * multiplier * opportunityScale,
                -maximumProjectionLatitude, maximumProjectionLatitude);
    }

    public static Projection project(Event event, CelestialVector moonDirection,
                                     CelestialVector sunDirection, CelestialVector celestialNorth,
                                     double moonHalfTangent, double shadowHalfTangent) {
        LunarEclipseState state = projectState(event, moonDirection, sunDirection, celestialNorth,
                moonHalfTangent, shadowHalfTangent);
        if (state == LunarEclipseState.NONE) {
            return Projection.NONE;
        }
        CelestialDiscGeometry.AlignedSquare shadow = new CelestialDiscGeometry.AlignedSquare(
                state.shadowCenterX(), state.shadowCenterY(), state.shadowRadius(), true);
        return new Projection(shadow, state);
    }

    /** State-only path used by hot celestial samples that never observe the Projection wrapper. */
    static LunarEclipseState projectState(Event event, CelestialVector moonDirection,
                                          CelestialVector sunDirection,
                                          CelestialVector celestialNorth,
                                          double moonHalfTangent,
                                          double shadowHalfTangent) {
        CoverageOutput output = projectCoverages(event, moonDirection, sunDirection,
                celestialNorth, moonHalfTangent, shadowHalfTangent);
        if (!output.valid) {
            return LunarEclipseState.NONE;
        }
        return new LunarEclipseState(event.fullMoonIndex(),
                event.fullMoonDay() * CelestialMath.TICKS_IN_DAY,
                event.lunarLatitudeRadians(), event.effectiveLatitudeRadians(),
                output.centerX, output.centerY, output.radius,
                output.umbraCoverage, output.penumbraCoverage);
    }

    /**
     * Ephemeral scalar-only projection used by display-event scans. The returned output belongs to
     * the current thread and must be consumed before another lunar projection on that thread.
     */
    static CoverageOutput projectCoverages(Event event, CelestialVector moonDirection,
                                            CelestialVector sunDirection,
                                            CelestialVector celestialNorth,
                                            double moonHalfTangent,
                                            double shadowHalfTangent) {
        CoverageOutput output = PROJECTION_OUTPUT.get();
        output.clear();
        if (event == null || !event.valid() || !positiveFinite(moonHalfTangent)
                || !positiveFinite(shadowHalfTangent)) {
            return output;
        }
        CelestialDiscGeometry.AlignedSquare raw =
                CelestialDiscGeometry.alignedSquareProjectionNegatedShadow(
                        moonDirection, sunDirection, celestialNorth,
                        moonHalfTangent, shadowHalfTangent);
        if (!raw.valid()) {
            return output;
        }

        // At exact full Moon raw centerY is -tan(lunarLatitude)/moonHalfTangent. Add only the
        // stable event latitude delta, preserving the raw orbit's continuous vertical drift.
        double additionalProjection = projectionDelta(event);
        double centerY = raw.centerY() - additionalProjection / moonHalfTangent;
        if (!Double.isFinite(centerY)) {
            return output;
        }
        double umbra = CelestialDiscGeometry.alignedSquareCoverage(
                raw.centerX(), centerY, raw.radius());
        double expandedRadius = raw.radius()
                + CelestialDiscGeometry.LUNAR_PENUMBRA_NORMALIZED_WIDTH;
        double penumbra = CelestialDiscGeometry.alignedSquareCoverage(
                raw.centerX(), centerY, expandedRadius);
        output.set(raw.centerX(), centerY, raw.radius(), umbra, penumbra);
        return output;
    }

    /** Exact umbra-only subset for gameplay checks that never consume penumbra geometry. */
    static double projectUmbraCoverage(Event event, CelestialVector moonDirection,
                                       CelestialVector sunDirection,
                                       CelestialVector celestialNorth,
                                       double moonHalfTangent,
                                       double shadowHalfTangent) {
        if (event == null || !event.valid() || !positiveFinite(moonHalfTangent)
                || !positiveFinite(shadowHalfTangent)) {
            return 0.0D;
        }
        CelestialDiscGeometry.AlignedSquare raw =
                CelestialDiscGeometry.alignedSquareProjectionNegatedShadow(
                        moonDirection, sunDirection, celestialNorth,
                        moonHalfTangent, shadowHalfTangent);
        if (!raw.valid()) {
            return 0.0D;
        }
        double additionalProjection = projectionDelta(event);
        double centerY = raw.centerY() - additionalProjection / moonHalfTangent;
        if (!Double.isFinite(centerY)) {
            return 0.0D;
        }
        return CelestialDiscGeometry.alignedSquareCoverage(
                raw.centerX(), centerY, raw.radius());
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

    static double projectionDelta(Event event) {
        return PROJECTION_DELTA_CACHE.get().get(event);
    }

    private static final class OpportunityScaleCache {
        private long daysInYearBits;
        private long synodicDaysBits;
        private double value;
        private boolean initialized;

        private double get(double daysInYear, double synodicDays) {
            long nextDaysInYearBits = Double.doubleToRawLongBits(daysInYear);
            long nextSynodicDaysBits = Double.doubleToRawLongBits(synodicDays);
            if (!initialized || daysInYearBits != nextDaysInYearBits
                    || synodicDaysBits != nextSynodicDaysBits) {
                double referenceOpportunities = REFERENCE_YEAR_DAYS / CelestialMath.SYNODIC_DAYS;
                double annualOpportunities = positiveFinite(daysInYear)
                        && positiveFinite(synodicDays)
                        ? daysInYear / synodicDays : referenceOpportunities;
                double computed = Math.pow(annualOpportunities / referenceOpportunities,
                        ANNUAL_OPPORTUNITY_EXPONENT);
                daysInYearBits = nextDaysInYearBits;
                synodicDaysBits = nextSynodicDaysBits;
                value = computed;
                initialized = true;
            }
            return value;
        }
    }

    private static final class EventCache {
        private long fullMoonIndex;
        private long daysInYearBits;
        private long synodicDaysBits;
        private long nodalYearsBits;
        private long lunarInclinationBits;
        private long sineLunarInclinationBits;
        private Event value;
        private boolean initialized;

        private Event get(long fullMoonIndex, double daysInYear, double synodicDays,
                          double nodalYears, double lunarInclination,
                          double sineLunarInclination) {
            long nextDaysInYearBits = Double.doubleToRawLongBits(daysInYear);
            long nextSynodicDaysBits = Double.doubleToRawLongBits(synodicDays);
            long nextNodalYearsBits = Double.doubleToRawLongBits(nodalYears);
            long nextLunarInclinationBits = Double.doubleToRawLongBits(lunarInclination);
            long nextSineLunarInclinationBits =
                    Double.doubleToRawLongBits(sineLunarInclination);
            if (!initialized || this.fullMoonIndex != fullMoonIndex
                    || daysInYearBits != nextDaysInYearBits
                    || synodicDaysBits != nextSynodicDaysBits
                    || nodalYearsBits != nextNodalYearsBits
                    || lunarInclinationBits != nextLunarInclinationBits
                    || sineLunarInclinationBits != nextSineLunarInclinationBits) {
                Event computed = eventAt(fullMoonIndex, daysInYear, synodicDays, nodalYears,
                        lunarInclination, sineLunarInclination);
                this.fullMoonIndex = fullMoonIndex;
                daysInYearBits = nextDaysInYearBits;
                synodicDaysBits = nextSynodicDaysBits;
                nodalYearsBits = nextNodalYearsBits;
                lunarInclinationBits = nextLunarInclinationBits;
                sineLunarInclinationBits = nextSineLunarInclinationBits;
                value = computed;
                initialized = true;
            }
            return value;
        }
    }

    private static final class ProjectionDeltaCache {
        private Event eventIdentity;
        private double value;

        private double get(Event event) {
            if (eventIdentity != event) {
                double computed = Math.tan(event.effectiveLatitudeRadians())
                        - Math.tan(event.lunarLatitudeRadians());
                eventIdentity = event;
                value = computed;
            }
            return value;
        }
    }

    static final class CoverageOutput {
        private double centerX;
        private double centerY;
        private double radius;
        private double umbraCoverage;
        private double penumbraCoverage;
        private boolean valid;

        private void clear() {
            centerX = 0.0D;
            centerY = 0.0D;
            radius = 0.0D;
            umbraCoverage = 0.0D;
            penumbraCoverage = 0.0D;
            valid = false;
        }

        private void set(double centerX, double centerY, double radius,
                         double umbraCoverage, double penumbraCoverage) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.umbraCoverage = umbraCoverage;
            this.penumbraCoverage = penumbraCoverage;
            valid = true;
        }

        double umbraCoverage() {
            return umbraCoverage;
        }

        double penumbraCoverage() {
            return penumbraCoverage;
        }
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
