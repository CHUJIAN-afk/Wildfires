package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.api.celestial.LunarEclipseState;
import first.wildfires.api.celestial.SolarEclipseState;

/** Pure deterministic astronomy math. This class intentionally has no Minecraft or Forge references. */
public final class CelestialMath {

    public static final double TAU = Math.PI * 2.0D;
    public static final double TICKS_IN_DAY = 24000.0D;
    public static final int MONTHS_IN_YEAR = 12;
    public static final double DEG_TO_RAD = Math.PI / 180.0D;
    public static final double AXIAL_TILT = 23.44D * DEG_TO_RAD;
    public static final double AXIAL_TILT_SIN = Math.sin(AXIAL_TILT);
    public static final double AXIAL_TILT_COS = Math.cos(AXIAL_TILT);
    public static final double LUNAR_INCLINATION = 5.14D * DEG_TO_RAD;
    public static final double SYNODIC_DAYS = 16.13D;
    public static final double ANOMALISTIC_DAYS = SYNODIC_DAYS * 27.55455D / 29.530588D;
    public static final double NODAL_YEARS = 18.6D;
    public static final double SUN_ANGULAR_RADIUS = 0.2666D * DEG_TO_RAD;
    public static final double MOON_ANGULAR_RADIUS = 0.2725D * DEG_TO_RAD;
    public static final double MOON_MEAN_DISTANCE_MILLION_KM = 0.3844D;
    public static final double SUPERMOON_FULL_MOON_HALF_WINDOW_DAYS = 0.5D;
    private static final CelestialVector EQUATORIAL_NORTH_ECLIPTIC =
            new CelestialVector(0.0D, AXIAL_TILT_SIN, AXIAL_TILT_COS);
    private static final CelestialVector EQUATORIAL_NORTH =
            new CelestialVector(0.0D, 0.0D, 1.0D);
    private static final ThreadLocal<SupermoonCache> SUPERMOON_CACHE =
            ThreadLocal.withInitial(SupermoonCache::new);

    private CelestialMath() {
    }

    public static double latitude(double z, double hemisphereScale) {
        if (!Double.isFinite(hemisphereScale) || Math.abs(hemisphereScale) < 1.0E-6D) {
            return Math.PI * 0.25D;
        }
        double amplitude = -Math.PI * 0.5D;
        double frequency = 1.0D / (4.0D * hemisphereScale);
        double value = z - 0.5D * hemisphereScale;
        return amplitude * (Math.abs(4.0D * frequency * value + 1.0D
                - 4.0D * Math.floor(frequency * value + 0.75D)) - 1.0D);
    }

    public static Result calculate(Input input) {
        return calculate(input.z, input.hemisphereScale, input.calendarTicks, input.daysInMonth,
                input.synodicDays, input.anomalisticDays, input.nodalYears,
                input.lunarInclination, input.sunScale, input.moonScale);
    }

    /** Allocation-free package path for callers that already hold the validated scalar inputs. */
    static Result calculate(double z, double hemisphereScale, double calendarTicks,
                            int daysInMonth, double synodicDays, double anomalisticDays,
                            double nodalYears, double lunarInclination,
                            double sunScale, double moonScale) {
        return calculate(z, hemisphereScale, calendarTicks, daysInMonth, synodicDays,
                anomalisticDays, nodalYears, lunarInclination, sunScale, moonScale,
                Math.sin(lunarInclination));
    }

    /** Prepared path for repeated frames that share the same lunar-inclination setting. */
    static Result calculate(double z, double hemisphereScale, double calendarTicks,
                            int daysInMonth, double synodicDays, double anomalisticDays,
                            double nodalYears, double lunarInclination,
                            double sunScale, double moonScale,
                            double sineLunarInclination) {
        return calculate(z, hemisphereScale, calendarTicks, daysInMonth, synodicDays,
                anomalisticDays, nodalYears, lunarInclination, sunScale, moonScale,
                sineLunarInclination, null);
    }

    /**
     * Prepared full-frame path that also publishes the exact horizon products already evaluated
     * by this calculation.  The mutable sink is package-private and thread-confined by its caller;
     * it does not become part of the public immutable {@link Result} contract.
     */
    static Result calculate(double z, double hemisphereScale, double calendarTicks,
                            int daysInMonth, double synodicDays, double anomalisticDays,
                            double nodalYears, double lunarInclination,
                            double sunScale, double moonScale,
                            double sineLunarInclination, HorizonProducts horizonProducts) {
        double daysInYear = daysInYear(daysInMonth);
        double calendarDays = calendarDays(calendarTicks);
        double fractionOfDay = positiveModulo(calendarTicks, TICKS_IN_DAY) / TICKS_IN_DAY;
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double latitude = latitude(z, hemisphereScale);

        double solarLongitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        CelestialVector rawSun = eclipticToEquatorialFixedTilt(solarLongitude, 0.0D);
        double declination = AXIAL_TILT * Math.sin(solarLongitude);
        double sunRightAscension = Math.atan2(rawSun.y(), rawSun.x());
        double sineDeclination = Math.sin(declination);
        double cosineDeclination = Math.cos(declination);
        CelestialVector sunEquatorial = fromRightAscension(sunRightAscension,
                sineDeclination, cosineDeclination);
        double localSiderealAngle = sunRightAscension + TAU * (fractionOfDay - 0.5D);
        double cosineSidereal = Math.cos(localSiderealAngle);
        double sineSidereal = Math.sin(localSiderealAngle);
        double sineLatitude = Math.sin(latitude);
        double cosineLatitude = Math.cos(latitude);
        if (horizonProducts != null) {
            horizonProducts.set(sineLatitude, cosineLatitude, sineSidereal, cosineSidereal);
        }
        CelestialVector sunDirection = equatorialToHorizon(sunEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double synodicProgress = positiveModulo(calendarDays / synodicDays, 1.0D);
        double elongation = Math.PI + TAU * synodicProgress;
        double moonLongitude = solarLongitude + elongation;
        double ascendingNode = lunarAscendingNode(calendarDays, daysInYear, nodalYears);
        double moonLatitude = Math.asin(sineLunarInclination
                * Math.sin(moonLongitude - ascendingNode));
        CelestialVector moonEquatorial = eclipticToEquatorialFixedTilt(moonLongitude, moonLatitude);
        CelestialVector rawMoonDirection = equatorialToHorizon(moonEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double anomalisticProgress = positiveModulo(calendarDays / anomalisticDays, 1.0D);
        double moonDistance = 1.0D - 0.07D * Math.cos(TAU * anomalisticProgress);
        double moonRadius = MOON_ANGULAR_RADIUS / moonDistance;
        double sunMoonSeparation = angle(sunDirection, rawMoonDirection);
        CelestialVector celestialNorth = equatorialToHorizon(EQUATORIAL_NORTH, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);
        double physicalSolarEclipse = circleCoverage(SUN_ANGULAR_RADIUS, moonRadius, sunMoonSeparation);
        double sunPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.sunBodyHalfSize(sunScale));
        double moonBodyHalfSize = CelestialDiscGeometry.moonBodyHalfSize(moonScale, moonDistance);
        double moonPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize,
                CelestialDiscGeometry.PIXEL_COVER_RADIUS);
        SolarEclipseRegion.Event solarEvent = SolarEclipseRegion.eventForPrepared(
                calendarDays, daysInYear,
                synodicDays, nodalYears, lunarInclination, sineLunarInclination);
        SolarEclipseRegion.Projection solarProjection = SolarEclipseRegion.project(solarEvent,
                calendarDays, latitude, sunDirection, rawMoonDirection, celestialNorth,
                sunPixelHalfTangent, moonPixelHalfTangent, synodicDays);
        CelestialVector moonDirection = solarProjection.moonDirection();
        double solarEclipse = solarProjection.coverage();
        double lunarPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize);
        LunarEclipseRegion.Event lunarEvent = LunarEclipseRegion.eventForPrepared(
                calendarDays, daysInYear,
                synodicDays, nodalYears, lunarInclination, sineLunarInclination);
        LunarEclipseState lunarState = LunarEclipseRegion.projectState(lunarEvent,
                rawMoonDirection, sunDirection, celestialNorth,
                lunarPixelHalfTangent, lunarPixelHalfTangent);
        double lunarEclipse = lunarState.umbraCoverage();
        // Phase illumination remains a property of the physical three-dimensional orbit.
        // The regional apparent direction may move a non-eclipse conjunction clear of the
        // enlarged pixel Sun, but it must not erase the 16.13-day new/full Moon cycle.
        double illuminated = clamp((1.0D - sunDirection.dot(rawMoonDirection)) * 0.5D, 0.0D, 1.0D);
        int moonPhase = moonPhaseFromGeometry(synodicProgress, sunMoonSeparation);
        double supermoon = supermoonAtFullMoon(calendarDays, synodicDays, anomalisticDays);
        double solarElevation = Math.asin(clamp(sunDirection.y(), -1.0D, 1.0D));
        double moonElevation = Math.asin(clamp(moonDirection.y(), -1.0D, 1.0D));
        double daylight = smoothstep(-6.0D * DEG_TO_RAD, 0.0D, solarElevation);
        double apparentDayTime = sunBasedDayTimeFromProducts(
                sineLatitude * sineDeclination,
                cosineLatitude * cosineDeclination, fractionOfDay) / 24000.0D;
        return new Result(latitude, fractionOfDay, fractionOfYear, sunEquatorial, moonEquatorial,
                sunDirection, moonDirection, celestialNorth, moonDistance, moonRadius, sunMoonSeparation,
                illuminated, moonPhase, solarEclipse, physicalSolarEclipse, lunarEclipse,
                lunarState, supermoon, lunarEclipse,
                solarProjection.state(), solarElevation, moonElevation, apparentDayTime, daylight,
                solarLongitude, localSiderealAngle);
    }

    /** Exact moon-distance subset used when prediction geometry needs no other frame fields. */
    static double moonDistanceAtCalendarTicks(double calendarTicks, double anomalisticDays) {
        double calendarDays = calendarDays(calendarTicks);
        double anomalisticProgress = positiveModulo(calendarDays / anomalisticDays, 1.0D);
        return 1.0D - 0.07D * Math.cos(TAU * anomalisticProgress);
    }

    /**
     * Exact solar-only subset consumed by the public daylight query. Every retained operation is in
     * the same order as {@link #calculate}; lunar geometry, eclipse projections and body assembly
     * are omitted because {@code DaylightState} cannot observe them.
     */
    static DaylightSample daylightSampleAt(double z, double hemisphereScale,
                                            double calendarTicks, int daysInMonth) {
        double daysInYear = daysInYear(daysInMonth);
        double calendarDays = calendarDays(calendarTicks);
        double fractionOfDay = positiveModulo(calendarTicks, TICKS_IN_DAY) / TICKS_IN_DAY;
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double latitude = latitude(z, hemisphereScale);

        double solarLongitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        CelestialVector rawSun = eclipticToEquatorialFixedTilt(solarLongitude, 0.0D);
        double declination = AXIAL_TILT * Math.sin(solarLongitude);
        double sunRightAscension = Math.atan2(rawSun.y(), rawSun.x());
        double sineDeclination = Math.sin(declination);
        double cosineDeclination = Math.cos(declination);
        CelestialVector sunEquatorial = fromRightAscension(sunRightAscension,
                sineDeclination, cosineDeclination);
        double localSiderealAngle = sunRightAscension + TAU * (fractionOfDay - 0.5D);
        double cosineSidereal = Math.cos(localSiderealAngle);
        double sineSidereal = Math.sin(localSiderealAngle);
        double sineLatitude = Math.sin(latitude);
        double cosineLatitude = Math.cos(latitude);
        CelestialVector sunDirection = equatorialToHorizon(sunEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double solarElevation = Math.asin(clamp(sunDirection.y(), -1.0D, 1.0D));
        double daylight = smoothstep(-6.0D * DEG_TO_RAD, 0.0D, solarElevation);
        double apparentDayTime = sunBasedDayTimeFromProducts(
                sineLatitude * sineDeclination,
                cosineLatitude * cosineDeclination, fractionOfDay) / 24000.0D;
        return new DaylightSample(solarElevation, apparentDayTime, daylight);
    }

    /** Exact solar-only subset for repeated event scans at one prepared TFE latitude. */
    static DaylightSample daylightSampleAt(ObserverLatitudeContext observerLatitude,
                                            double calendarTicks, int daysInMonth) {
        double daysInYear = daysInYear(daysInMonth);
        double calendarDays = calendarDays(calendarTicks);
        double fractionOfDay = positiveModulo(calendarTicks, TICKS_IN_DAY) / TICKS_IN_DAY;
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;

        double solarLongitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        CelestialVector rawSun = eclipticToEquatorialFixedTilt(solarLongitude, 0.0D);
        double declination = AXIAL_TILT * Math.sin(solarLongitude);
        double sunRightAscension = Math.atan2(rawSun.y(), rawSun.x());
        double sineDeclination = Math.sin(declination);
        double cosineDeclination = Math.cos(declination);
        CelestialVector sunEquatorial = fromRightAscension(sunRightAscension,
                sineDeclination, cosineDeclination);
        double localSiderealAngle = sunRightAscension + TAU * (fractionOfDay - 0.5D);
        double cosineSidereal = Math.cos(localSiderealAngle);
        double sineSidereal = Math.sin(localSiderealAngle);
        double sineLatitude = observerLatitude.sine();
        double cosineLatitude = observerLatitude.cosine();
        CelestialVector sunDirection = equatorialToHorizon(sunEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double solarElevation = Math.asin(clamp(sunDirection.y(), -1.0D, 1.0D));
        double daylight = smoothstep(-6.0D * DEG_TO_RAD, 0.0D, solarElevation);
        double apparentDayTime = sunBasedDayTimeFromProducts(
                sineLatitude * sineDeclination,
                cosineLatitude * cosineDeclination, fractionOfDay) / 24000.0D;
        return new DaylightSample(solarElevation, apparentDayTime, daylight);
    }

    /**
     * Exact physical phase subset. The operation order intentionally mirrors {@link #calculate}
     * through the raw Sun/Moon horizon directions; regional eclipse display offsets are excluded
     * because the authoritative illuminated fraction also uses the unmodified lunar orbit.
     */
    static double illuminatedFractionAt(double z, double hemisphereScale, double calendarTicks,
                                        int daysInMonth, double synodicDays, double nodalYears,
                                        double lunarInclination) {
        return illuminatedFractionAt(z, hemisphereScale, calendarTicks, daysInMonth,
                synodicDays, nodalYears, lunarInclination, Math.sin(lunarInclination));
    }

    static double illuminatedFractionAt(double z, double hemisphereScale, double calendarTicks,
                                        int daysInMonth, double synodicDays, double nodalYears,
                                        double lunarInclination,
                                        double sineLunarInclination) {
        return illuminatedFractionAt(prepareSolarLatitude(latitude(z, hemisphereScale)),
                calendarTicks, daysInMonth, synodicDays, nodalYears, lunarInclination,
                sineLunarInclination);
    }

    static double illuminatedFractionAt(SolarLatitudeContext observerLatitude,
                                         double calendarTicks, int daysInMonth,
                                         double synodicDays, double nodalYears,
                                         double lunarInclination,
                                         double sineLunarInclination) {
        double daysInYear = daysInYear(daysInMonth);
        double calendarDays = calendarDays(calendarTicks);
        double fractionOfDay = positiveModulo(calendarTicks, TICKS_IN_DAY) / TICKS_IN_DAY;
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;

        double solarLongitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        CelestialVector rawSun = eclipticToEquatorialFixedTilt(solarLongitude, 0.0D);
        double declination = AXIAL_TILT * Math.sin(solarLongitude);
        double sunRightAscension = Math.atan2(rawSun.y(), rawSun.x());
        double sineDeclination = Math.sin(declination);
        double cosineDeclination = Math.cos(declination);
        CelestialVector sunEquatorial = fromRightAscension(sunRightAscension,
                sineDeclination, cosineDeclination);
        double localSiderealAngle = sunRightAscension + TAU * (fractionOfDay - 0.5D);
        double cosineSidereal = Math.cos(localSiderealAngle);
        double sineSidereal = Math.sin(localSiderealAngle);
        double sineLatitude = observerLatitude.sine();
        double cosineLatitude = observerLatitude.cosine();
        CelestialVector sunDirection = equatorialToHorizon(sunEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double synodicProgress = positiveModulo(calendarDays / synodicDays, 1.0D);
        double elongation = Math.PI + TAU * synodicProgress;
        double moonLongitude = solarLongitude + elongation;
        double ascendingNode = lunarAscendingNode(calendarDays, daysInYear, nodalYears);
        double moonLatitude = Math.asin(sineLunarInclination
                * Math.sin(moonLongitude - ascendingNode));
        CelestialVector moonEquatorial = eclipticToEquatorialFixedTilt(moonLongitude, moonLatitude);
        CelestialVector rawMoonDirection = equatorialToHorizon(moonEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);
        return clamp((1.0D - sunDirection.dot(rawMoonDirection)) * 0.5D, 0.0D, 1.0D);
    }

    /** Exact lunar-eclipse/supermoon subset used by the bounded prediction scanner. */
    static LunarPredictionSample lunarPredictionSampleAt(
            double z, double hemisphereScale, double calendarTicks, int daysInMonth,
            double synodicDays, double anomalisticDays, double nodalYears,
            double lunarInclination, double moonScale) {
        return lunarPredictionSampleAt(z, hemisphereScale, calendarTicks, daysInMonth,
                synodicDays, anomalisticDays, nodalYears, lunarInclination, moonScale,
                Math.sin(lunarInclination));
    }

    static LunarPredictionSample lunarPredictionSampleAt(
            double z, double hemisphereScale, double calendarTicks, int daysInMonth,
            double synodicDays, double anomalisticDays, double nodalYears,
            double lunarInclination, double moonScale, double sineLunarInclination) {
        return lunarPredictionSampleAt(prepareSolarLatitude(latitude(z, hemisphereScale)),
                calendarTicks, daysInMonth, synodicDays, anomalisticDays, nodalYears,
                lunarInclination, moonScale, sineLunarInclination);
    }

    static LunarPredictionSample lunarPredictionSampleAt(
            SolarLatitudeContext observerLatitude, double calendarTicks, int daysInMonth,
            double synodicDays, double anomalisticDays, double nodalYears,
            double lunarInclination, double moonScale, double sineLunarInclination) {
        double daysInYear = daysInYear(daysInMonth);
        double calendarDays = calendarDays(calendarTicks);
        double fractionOfDay = positiveModulo(calendarTicks, TICKS_IN_DAY) / TICKS_IN_DAY;
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;

        double solarLongitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        CelestialVector rawSun = eclipticToEquatorialFixedTilt(solarLongitude, 0.0D);
        double declination = AXIAL_TILT * Math.sin(solarLongitude);
        double sunRightAscension = Math.atan2(rawSun.y(), rawSun.x());
        double sineDeclination = Math.sin(declination);
        double cosineDeclination = Math.cos(declination);
        CelestialVector sunEquatorial = fromRightAscension(sunRightAscension,
                sineDeclination, cosineDeclination);
        double localSiderealAngle = sunRightAscension + TAU * (fractionOfDay - 0.5D);
        double cosineSidereal = Math.cos(localSiderealAngle);
        double sineSidereal = Math.sin(localSiderealAngle);
        double sineLatitude = observerLatitude.sine();
        double cosineLatitude = observerLatitude.cosine();
        CelestialVector sunDirection = equatorialToHorizon(sunEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double synodicProgress = positiveModulo(calendarDays / synodicDays, 1.0D);
        double elongation = Math.PI + TAU * synodicProgress;
        double moonLongitude = solarLongitude + elongation;
        double ascendingNode = lunarAscendingNode(calendarDays, daysInYear, nodalYears);
        double moonLatitude = Math.asin(sineLunarInclination
                * Math.sin(moonLongitude - ascendingNode));
        CelestialVector moonEquatorial = eclipticToEquatorialFixedTilt(moonLongitude, moonLatitude);
        CelestialVector rawMoonDirection = equatorialToHorizon(moonEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double anomalisticProgress = positiveModulo(calendarDays / anomalisticDays, 1.0D);
        double moonDistance = 1.0D - 0.07D * Math.cos(TAU * anomalisticProgress);
        CelestialVector celestialNorth = equatorialToHorizon(EQUATORIAL_NORTH, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);
        double moonBodyHalfSize = CelestialDiscGeometry.moonBodyHalfSize(moonScale, moonDistance);
        double lunarPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize);
        LunarEclipseRegion.Event lunarEvent = LunarEclipseRegion.eventForPrepared(
                calendarDays, daysInYear,
                synodicDays, nodalYears, lunarInclination, sineLunarInclination);
        LunarEclipseState lunarState = LunarEclipseRegion.projectState(lunarEvent,
                rawMoonDirection, sunDirection, celestialNorth,
                lunarPixelHalfTangent, lunarPixelHalfTangent);
        double supermoon = supermoonAtFullMoon(calendarDays, synodicDays, anomalisticDays);
        return new LunarPredictionSample(lunarState, supermoon);
    }

    /**
     * Exact subset consumed by local event queries. It deliberately follows the full calculation's
     * operation order while omitting physical-disc diagnostics, global solar-band metadata and
     * public body-state assembly that no event predicate reads.
     */
    static EventSample eventSampleAt(
            double z, double hemisphereScale, double calendarTicks, int daysInMonth,
            double synodicDays, double anomalisticDays, double nodalYears,
            double lunarInclination, double sunScale, double moonScale,
            double sineLunarInclination) {
        return eventSampleAt(prepareObserverLatitude(z, hemisphereScale), calendarTicks,
                daysInMonth, synodicDays, anomalisticDays, nodalYears, lunarInclination,
                sunScale, moonScale, sineLunarInclination);
    }

    /** Prepared path for scans whose observer remains on one TFE latitude grid point. */
    static EventSample eventSampleAt(
            ObserverLatitudeContext observerLatitude, double calendarTicks, int daysInMonth,
            double synodicDays, double anomalisticDays, double nodalYears,
            double lunarInclination, double sunScale, double moonScale,
            double sineLunarInclination) {
        double daysInYear = daysInYear(daysInMonth);
        double calendarDays = calendarDays(calendarTicks);
        double fractionOfDay = positiveModulo(calendarTicks, TICKS_IN_DAY) / TICKS_IN_DAY;
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double latitude = observerLatitude.latitude();

        double solarLongitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        CelestialVector rawSun = eclipticToEquatorialFixedTilt(solarLongitude, 0.0D);
        double declination = AXIAL_TILT * Math.sin(solarLongitude);
        double sunRightAscension = Math.atan2(rawSun.y(), rawSun.x());
        double sineDeclination = Math.sin(declination);
        double cosineDeclination = Math.cos(declination);
        CelestialVector sunEquatorial = fromRightAscension(sunRightAscension,
                sineDeclination, cosineDeclination);
        double localSiderealAngle = sunRightAscension + TAU * (fractionOfDay - 0.5D);
        double cosineSidereal = Math.cos(localSiderealAngle);
        double sineSidereal = Math.sin(localSiderealAngle);
        double sineLatitude = observerLatitude.sine();
        double cosineLatitude = observerLatitude.cosine();
        CelestialVector sunDirection = equatorialToHorizon(sunEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double synodicProgress = positiveModulo(calendarDays / synodicDays, 1.0D);
        double elongation = Math.PI + TAU * synodicProgress;
        double moonLongitude = solarLongitude + elongation;
        double ascendingNode = lunarAscendingNode(calendarDays, daysInYear, nodalYears);
        double moonLatitude = Math.asin(sineLunarInclination
                * Math.sin(moonLongitude - ascendingNode));
        CelestialVector moonEquatorial = eclipticToEquatorialFixedTilt(moonLongitude, moonLatitude);
        CelestialVector rawMoonDirection = equatorialToHorizon(moonEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double anomalisticProgress = positiveModulo(calendarDays / anomalisticDays, 1.0D);
        double moonDistance = 1.0D - 0.07D * Math.cos(TAU * anomalisticProgress);
        double sunMoonSeparation = angle(sunDirection, rawMoonDirection);
        CelestialVector celestialNorth = equatorialToHorizon(EQUATORIAL_NORTH, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);
        double sunPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.sunBodyHalfSize(sunScale));
        double moonBodyHalfSize = CelestialDiscGeometry.moonBodyHalfSize(moonScale, moonDistance);
        double moonPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize,
                CelestialDiscGeometry.PIXEL_COVER_RADIUS);
        SolarEclipseRegion.Event solarEvent = SolarEclipseRegion.eventForPrepared(
                calendarDays, daysInYear,
                synodicDays, nodalYears, lunarInclination, sineLunarInclination);
        SolarEclipseRegion.Projection solarProjection = SolarEclipseRegion.projectLocal(solarEvent,
                calendarDays, latitude, sunDirection, rawMoonDirection, celestialNorth,
                sunPixelHalfTangent, moonPixelHalfTangent, synodicDays);
        CelestialVector moonDirection = solarProjection.moonDirection();
        double solarEclipse = solarProjection.coverage();
        double lunarPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize);
        LunarEclipseRegion.Event lunarEvent = LunarEclipseRegion.eventForPrepared(
                calendarDays, daysInYear,
                synodicDays, nodalYears, lunarInclination, sineLunarInclination);
        LunarEclipseState lunarState = LunarEclipseRegion.projectState(lunarEvent,
                rawMoonDirection, sunDirection, celestialNorth,
                lunarPixelHalfTangent, lunarPixelHalfTangent);
        double lunarEclipse = lunarState.umbraCoverage();
        double illuminated = clamp((1.0D - sunDirection.dot(rawMoonDirection)) * 0.5D,
                0.0D, 1.0D);
        int moonPhase = moonPhaseFromGeometry(synodicProgress, sunMoonSeparation);
        double supermoon = supermoonAtFullMoon(calendarDays, synodicDays, anomalisticDays);
        double solarElevation = Math.asin(clamp(sunDirection.y(), -1.0D, 1.0D));
        double moonElevation = Math.asin(clamp(moonDirection.y(), -1.0D, 1.0D));
        double apparentDayTime = sunBasedDayTimeFromProducts(
                sineLatitude * sineDeclination,
                cosineLatitude * cosineDeclination, fractionOfDay) / 24000.0D;
        return new EventSample(latitude, fractionOfDay, illuminated, moonPhase,
                solarEclipse, lunarState, supermoon, lunarEclipse,
                solarElevation, moonElevation, apparentDayTime);
    }

    /**
     * Exact subset read by the six planetarium/current-event rows. The operation order of every
     * retained field matches {@link #eventSampleAt}; phase-cell, apparent-time and angular-separation
     * work is omitted because those rows never query it.
     */
    static DisplayEventSample displayEventSampleAt(
            double z, double hemisphereScale, double calendarTicks, int daysInMonth,
            double synodicDays, double anomalisticDays, double nodalYears,
            double lunarInclination, double sunScale, double moonScale,
            double sineLunarInclination) {
        return displayEventSampleAt(prepareObserverLatitude(z, hemisphereScale), calendarTicks,
                daysInMonth, synodicDays, anomalisticDays, nodalYears, lunarInclination,
                sunScale, moonScale, sineLunarInclination);
    }

    /** Prepared display-only path for bounded scans at one observer latitude. */
    static DisplayEventSample displayEventSampleAt(
            ObserverLatitudeContext observerLatitude, double calendarTicks, int daysInMonth,
            double synodicDays, double anomalisticDays, double nodalYears,
            double lunarInclination, double sunScale, double moonScale,
            double sineLunarInclination) {
        double daysInYear = daysInYear(daysInMonth);
        double calendarDays = calendarDays(calendarTicks);
        double fractionOfDay = positiveModulo(calendarTicks, TICKS_IN_DAY) / TICKS_IN_DAY;
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double latitude = observerLatitude.latitude();

        double solarLongitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        CelestialVector rawSun = eclipticToEquatorialFixedTilt(solarLongitude, 0.0D);
        double declination = AXIAL_TILT * Math.sin(solarLongitude);
        double sunRightAscension = Math.atan2(rawSun.y(), rawSun.x());
        double sineDeclination = Math.sin(declination);
        double cosineDeclination = Math.cos(declination);
        CelestialVector sunEquatorial = fromRightAscension(sunRightAscension,
                sineDeclination, cosineDeclination);
        double localSiderealAngle = sunRightAscension + TAU * (fractionOfDay - 0.5D);
        double cosineSidereal = Math.cos(localSiderealAngle);
        double sineSidereal = Math.sin(localSiderealAngle);
        double sineLatitude = observerLatitude.sine();
        double cosineLatitude = observerLatitude.cosine();
        CelestialVector sunDirection = equatorialToHorizon(sunEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double synodicProgress = positiveModulo(calendarDays / synodicDays, 1.0D);
        double elongation = Math.PI + TAU * synodicProgress;
        double moonLongitude = solarLongitude + elongation;
        double ascendingNode = lunarAscendingNode(calendarDays, daysInYear, nodalYears);
        double moonLatitude = Math.asin(sineLunarInclination
                * Math.sin(moonLongitude - ascendingNode));
        CelestialVector moonEquatorial = eclipticToEquatorialFixedTilt(moonLongitude, moonLatitude);
        CelestialVector rawMoonDirection = equatorialToHorizon(moonEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double anomalisticProgress = positiveModulo(calendarDays / anomalisticDays, 1.0D);
        double moonDistance = 1.0D - 0.07D * Math.cos(TAU * anomalisticProgress);
        CelestialVector celestialNorth = equatorialToHorizon(EQUATORIAL_NORTH, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);
        double sunPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.sunBodyHalfSize(sunScale));
        double moonBodyHalfSize = CelestialDiscGeometry.moonBodyHalfSize(moonScale, moonDistance);
        double moonPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize,
                CelestialDiscGeometry.PIXEL_COVER_RADIUS);
        SolarEclipseRegion.Event solarEvent = SolarEclipseRegion.eventForPrepared(
                calendarDays, daysInYear,
                synodicDays, nodalYears, lunarInclination, sineLunarInclination);
        SolarEclipseRegion.Projection solarProjection = SolarEclipseRegion.projectLocal(solarEvent,
                calendarDays, latitude, sunDirection, rawMoonDirection, celestialNorth,
                sunPixelHalfTangent, moonPixelHalfTangent, synodicDays);
        CelestialVector moonDirection = solarProjection.moonDirection();
        double solarEclipse = solarProjection.coverage();
        double lunarPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize);
        LunarEclipseRegion.Event lunarEvent = LunarEclipseRegion.eventForPrepared(
                calendarDays, daysInYear,
                synodicDays, nodalYears, lunarInclination, sineLunarInclination);
        LunarEclipseRegion.CoverageOutput lunarCoverages = LunarEclipseRegion.projectCoverages(
                lunarEvent,
                rawMoonDirection, sunDirection, celestialNorth,
                lunarPixelHalfTangent, lunarPixelHalfTangent);
        double lunarUmbraCoverage = lunarCoverages.umbraCoverage();
        double lunarPenumbraCoverage = lunarCoverages.penumbraCoverage();
        double illuminated = clamp((1.0D - sunDirection.dot(rawMoonDirection)) * 0.5D,
                0.0D, 1.0D);
        double supermoon = supermoonAtFullMoon(calendarDays, synodicDays, anomalisticDays);
        double solarElevation = Math.asin(clamp(sunDirection.y(), -1.0D, 1.0D));
        double moonElevation = Math.asin(clamp(moonDirection.y(), -1.0D, 1.0D));
        return new DisplayEventSample(illuminated, solarEclipse, lunarPenumbraCoverage,
                supermoon, lunarUmbraCoverage, solarElevation, moonElevation);
    }

    /**
     * Exact subset read by the first/last-quarter predicates. Regional solar projection remains
     * part of this path because it is authoritative for the rendered Moon elevation; lunar-eclipse,
     * supermoon and apparent-time work is omitted because those predicates cannot observe it.
     */
    static QuarterEventSample quarterEventSampleAt(
            double z, double hemisphereScale, double calendarTicks, int daysInMonth,
            double synodicDays, double anomalisticDays, double nodalYears, double lunarInclination,
            double sunScale, double moonScale, double sineLunarInclination) {
        return quarterEventSampleAt(prepareObserverLatitude(z, hemisphereScale), calendarTicks,
                daysInMonth, synodicDays, anomalisticDays, nodalYears, lunarInclination,
                sunScale, moonScale, sineLunarInclination);
    }

    /** Prepared quarter-phase path for bounded scans at one observer latitude. */
    static QuarterEventSample quarterEventSampleAt(
            ObserverLatitudeContext observerLatitude, double calendarTicks, int daysInMonth,
            double synodicDays, double anomalisticDays, double nodalYears,
            double lunarInclination,
            double sunScale, double moonScale, double sineLunarInclination) {
        double daysInYear = daysInYear(daysInMonth);
        double calendarDays = calendarDays(calendarTicks);
        double fractionOfDay = positiveModulo(calendarTicks, TICKS_IN_DAY) / TICKS_IN_DAY;
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double latitude = observerLatitude.latitude();

        double solarLongitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        CelestialVector rawSun = eclipticToEquatorialFixedTilt(solarLongitude, 0.0D);
        double declination = AXIAL_TILT * Math.sin(solarLongitude);
        double sunRightAscension = Math.atan2(rawSun.y(), rawSun.x());
        double sineDeclination = Math.sin(declination);
        double cosineDeclination = Math.cos(declination);
        CelestialVector sunEquatorial = fromRightAscension(sunRightAscension,
                sineDeclination, cosineDeclination);
        double localSiderealAngle = sunRightAscension + TAU * (fractionOfDay - 0.5D);
        double cosineSidereal = Math.cos(localSiderealAngle);
        double sineSidereal = Math.sin(localSiderealAngle);
        double sineLatitude = observerLatitude.sine();
        double cosineLatitude = observerLatitude.cosine();
        CelestialVector sunDirection = equatorialToHorizon(sunEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double synodicProgress = positiveModulo(calendarDays / synodicDays, 1.0D);
        double elongation = Math.PI + TAU * synodicProgress;
        double moonLongitude = solarLongitude + elongation;
        double ascendingNode = lunarAscendingNode(calendarDays, daysInYear, nodalYears);
        double moonLatitude = Math.asin(sineLunarInclination
                * Math.sin(moonLongitude - ascendingNode));
        CelestialVector moonEquatorial = eclipticToEquatorialFixedTilt(moonLongitude, moonLatitude);
        CelestialVector rawMoonDirection = equatorialToHorizon(moonEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double anomalisticProgress = positiveModulo(calendarDays / anomalisticDays, 1.0D);
        double moonDistance = 1.0D - 0.07D * Math.cos(TAU * anomalisticProgress);
        double sunMoonSeparation = angle(sunDirection, rawMoonDirection);
        CelestialVector celestialNorth = equatorialToHorizon(EQUATORIAL_NORTH, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);
        double sunPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.sunBodyHalfSize(sunScale));
        double moonBodyHalfSize = CelestialDiscGeometry.moonBodyHalfSize(moonScale, moonDistance);
        double moonPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize,
                CelestialDiscGeometry.PIXEL_COVER_RADIUS);
        SolarEclipseRegion.Event solarEvent = SolarEclipseRegion.eventForPrepared(
                calendarDays, daysInYear,
                synodicDays, nodalYears, lunarInclination, sineLunarInclination);
        SolarEclipseRegion.Projection solarProjection = SolarEclipseRegion.projectLocal(solarEvent,
                calendarDays, latitude, sunDirection, rawMoonDirection, celestialNorth,
                sunPixelHalfTangent, moonPixelHalfTangent, synodicDays);
        CelestialVector moonDirection = solarProjection.moonDirection();
        double illuminated = clamp((1.0D - sunDirection.dot(rawMoonDirection)) * 0.5D,
                0.0D, 1.0D);
        int moonPhase = moonPhaseFromGeometry(synodicProgress, sunMoonSeparation);
        double solarElevation = Math.asin(clamp(sunDirection.y(), -1.0D, 1.0D));
        double moonElevation = Math.asin(clamp(moonDirection.y(), -1.0D, 1.0D));
        return new QuarterEventSample(illuminated, moonPhase, solarElevation, moonElevation);
    }

    /**
     * Exact final visible-blood-moon subset used by the server chunk cache. The umbra threshold is
     * tested before unrelated solar-eclipse display work; when active, the same regional Moon
     * direction and elevation formulas as {@link #displayEventSampleAt} are evaluated.
     */
    static double visibleBloodMoonAt(
            double z, double hemisphereScale, double calendarTicks, int daysInMonth,
            double synodicDays, double anomalisticDays, double nodalYears,
            double lunarInclination, double sunScale, double moonScale,
            double sineLunarInclination) {
        ObserverLatitudeContext observerLatitude = prepareObserverLatitude(z, hemisphereScale);
        double daysInYear = daysInYear(daysInMonth);
        double calendarDays = calendarDays(calendarTicks);
        double fractionOfDay = positiveModulo(calendarTicks, TICKS_IN_DAY) / TICKS_IN_DAY;
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double latitude = observerLatitude.latitude();

        double solarLongitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        CelestialVector rawSun = eclipticToEquatorialFixedTilt(solarLongitude, 0.0D);
        double declination = AXIAL_TILT * Math.sin(solarLongitude);
        double sunRightAscension = Math.atan2(rawSun.y(), rawSun.x());
        double sineDeclination = Math.sin(declination);
        double cosineDeclination = Math.cos(declination);
        CelestialVector sunEquatorial = fromRightAscension(sunRightAscension,
                sineDeclination, cosineDeclination);
        double localSiderealAngle = sunRightAscension + TAU * (fractionOfDay - 0.5D);
        double cosineSidereal = Math.cos(localSiderealAngle);
        double sineSidereal = Math.sin(localSiderealAngle);
        double sineLatitude = observerLatitude.sine();
        double cosineLatitude = observerLatitude.cosine();
        CelestialVector sunDirection = equatorialToHorizon(sunEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double synodicProgress = positiveModulo(calendarDays / synodicDays, 1.0D);
        double elongation = Math.PI + TAU * synodicProgress;
        double moonLongitude = solarLongitude + elongation;
        double ascendingNode = lunarAscendingNode(calendarDays, daysInYear, nodalYears);
        double moonLatitude = Math.asin(sineLunarInclination
                * Math.sin(moonLongitude - ascendingNode));
        CelestialVector moonEquatorial = eclipticToEquatorialFixedTilt(moonLongitude, moonLatitude);
        CelestialVector rawMoonDirection = equatorialToHorizon(moonEquatorial, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);

        double anomalisticProgress = positiveModulo(calendarDays / anomalisticDays, 1.0D);
        double moonDistance = 1.0D - 0.07D * Math.cos(TAU * anomalisticProgress);
        CelestialVector celestialNorth = equatorialToHorizon(EQUATORIAL_NORTH, sineLatitude,
                cosineLatitude, sineSidereal, cosineSidereal);
        double moonBodyHalfSize = CelestialDiscGeometry.moonBodyHalfSize(moonScale, moonDistance);
        double lunarPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize);
        LunarEclipseRegion.Event lunarEvent = LunarEclipseRegion.eventForPrepared(
                calendarDays, daysInYear,
                synodicDays, nodalYears, lunarInclination, sineLunarInclination);
        double bloodMoon = LunarEclipseRegion.projectUmbraCoverage(lunarEvent,
                rawMoonDirection, sunDirection, celestialNorth,
                lunarPixelHalfTangent, lunarPixelHalfTangent);
        if (!Double.isFinite(bloodMoon)
                || bloodMoon <= CelestialGameplayRules.ACTIVE_THRESHOLD) {
            return 0.0D;
        }

        double sunPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.sunBodyHalfSize(sunScale));
        double moonPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize,
                CelestialDiscGeometry.PIXEL_COVER_RADIUS);
        SolarEclipseRegion.Event solarEvent = SolarEclipseRegion.eventForPrepared(
                calendarDays, daysInYear,
                synodicDays, nodalYears, lunarInclination, sineLunarInclination);
        SolarEclipseRegion.Projection solarProjection = SolarEclipseRegion.projectLocal(solarEvent,
                calendarDays, latitude, sunDirection, rawMoonDirection, celestialNorth,
                sunPixelHalfTangent, moonPixelHalfTangent, synodicDays);
        CelestialVector moonDirection = solarProjection.moonDirection();
        double solarElevation = Math.asin(clamp(sunDirection.y(), -1.0D, 1.0D));
        double moonElevation = Math.asin(clamp(moonDirection.y(), -1.0D, 1.0D));
        return CelestialGameplayRules.visibleBloodMoon(
                bloodMoon, moonElevation, solarElevation);
    }

    /**
     * A supermoon is a near-perigee full Moon, not every anomalistic perigee. The strength is
     * sampled at the nearest exact full Moon and remains stable for that one TFC-day window so
     * the local night can fade from white to blue and back without the event switching mid-night.
     */
    static double supermoonAtFullMoon(double calendarDays, double synodicDays,
                                      double anomalisticDays) {
        if (!Double.isFinite(calendarDays) || !Double.isFinite(synodicDays)
                || synodicDays <= 0.0D || !Double.isFinite(anomalisticDays)
                || anomalisticDays <= 0.0D) {
            return 0.0D;
        }
        double fullMoonDay = Math.rint(calendarDays / synodicDays) * synodicDays;
        if (Math.abs(calendarDays - fullMoonDay) > SUPERMOON_FULL_MOON_HALF_WINDOW_DAYS) {
            return 0.0D;
        }
        return SUPERMOON_CACHE.get().get(fullMoonDay, anomalisticDays);
    }

    static double sunBasedDayTime(double latitude, double declination, double fractionOfDay) {
        return sunBasedDayTimeFromProducts(Math.sin(latitude) * Math.sin(declination),
                Math.cos(latitude) * Math.cos(declination), fractionOfDay);
    }

    private static double sunBasedDayTimeFromProducts(double sinLatitudeSinDeclination,
                                                       double cosLatitudeCosDeclination,
                                                       double fractionOfDay) {
        double currentElevation = calculateSunElevation(sinLatitudeSinDeclination,
                cosLatitudeCosDeclination, fractionOfDay);
        double midnightElevation = calculateSunElevation(sinLatitudeSinDeclination,
                cosLatitudeCosDeclination, 0.0D);
        double noonElevation = calculateSunElevation(sinLatitudeSinDeclination,
                cosLatitudeCosDeclination, 0.5D);
        return sunBasedDayTimeFromElevations(fractionOfDay, currentElevation,
                midnightElevation, noonElevation);
    }

    /**
     * TFC 1.21 maps solar elevation into vanilla's visual day-time quarters. Its direct horizon
     * branch has a zero-width interval when the daily minimum or maximum merely touches the
     * horizon, which can jump to the midpoint of the wrong quarter. Classifying the whole day as
     * normal, polar-day or polar-night first preserves the same mapping without that discontinuity.
     */
    static double sunBasedDayTimeFromElevations(double fractionOfDay, double currentElevation,
                                                double midnightElevation, double noonElevation) {
        if (!Double.isFinite(fractionOfDay) || !Double.isFinite(currentElevation)
                || !Double.isFinite(midnightElevation) || !Double.isFinite(noonElevation)) {
            return 18000.0D;
        }
        double dayFraction = positiveModulo(fractionOfDay, 1.0D);
        boolean morning = dayFraction < 0.5D;
        double horizonEpsilon = 1.0E-9D;
        boolean useDayArc;
        if (midnightElevation >= -horizonEpsilon) {
            useDayArc = true;
        } else if (noonElevation <= horizonEpsilon) {
            useDayArc = false;
        } else {
            useDayArc = currentElevation >= 0.0D;
        }
        if (useDayArc) {
            double dayProgress = clamp(currentElevation / Math.max(noonElevation, horizonEpsilon),
                    0.0D, 1.0D);
            return morning ? 6000.0D * dayProgress : 12000.0D - 6000.0D * dayProgress;
        }
        double nightProgress = clamp((currentElevation - midnightElevation)
                / Math.max(-midnightElevation, horizonEpsilon), 0.0D, 1.0D);
        return morning ? 18000.0D + 6000.0D * nightProgress
                : 18000.0D - 6000.0D * nightProgress;
    }

    private static double calculateSunElevation(double sinLatitudeSinDeclination,
                                                double cosLatitudeCosDeclination,
                                                double fractionOfDay) {
        double hourAngle = TAU * (0.5D - fractionOfDay);
        double cosineZenith = sinLatitudeSinDeclination
                + cosLatitudeCosDeclination * Math.cos(hourAngle);
        return Math.PI * 0.5D - Math.acos(clamp(cosineZenith, -1.0D, 1.0D));
    }

    /** Solar altitude for a latitude and absolute TFC day, used by local event visibility APIs. */
    public static double solarElevationAt(double latitudeRadians, double calendarDays,
                                          double daysInYear) {
        if (!Double.isFinite(latitudeRadians) || !Double.isFinite(calendarDays)
                || !Double.isFinite(daysInYear) || daysInYear <= 0.0D) {
            return Double.NaN;
        }
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double fractionOfDay = positiveModulo(calendarDays, 1.0D);
        double longitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        double declination = AXIAL_TILT * Math.sin(longitude);
        double hourAngle = TAU * (0.5D - fractionOfDay);
        double cosineZenith = Math.sin(latitudeRadians) * Math.sin(declination)
                + Math.cos(latitudeRadians) * Math.cos(declination) * Math.cos(hourAngle);
        return Math.asin(clamp(cosineZenith, -1.0D, 1.0D));
    }

    /** Prepared latitude path for scans that retain one observer latitude across many times. */
    static SolarLatitudeContext prepareSolarLatitude(double latitudeRadians) {
        if (!Double.isFinite(latitudeRadians)) {
            return SolarLatitudeContext.INVALID;
        }
        return new SolarLatitudeContext(Math.sin(latitudeRadians), Math.cos(latitudeRadians), true);
    }

    /** Preserves the exact TFE-grid latitude and trigonometric products for a fixed observer. */
    static ObserverLatitudeContext prepareObserverLatitude(double z, double hemisphereScale) {
        double latitude = latitude(z, hemisphereScale);
        double sine = Math.sin(latitude);
        double cosine = Math.cos(latitude);
        return new ObserverLatitudeContext(latitude, sine, cosine);
    }

    /**
     * Uses exactly the public solar-altitude expression while reusing the latitude trigonometry.
     * The multiplication and addition order intentionally matches {@link #solarElevationAt}.
     */
    static double solarElevationAt(SolarLatitudeContext latitude, double calendarDays,
                                   double daysInYear) {
        if (latitude == null || !latitude.valid() || !Double.isFinite(calendarDays)
                || !Double.isFinite(daysInYear) || daysInYear <= 0.0D) {
            return Double.NaN;
        }
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double fractionOfDay = positiveModulo(calendarDays, 1.0D);
        double longitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        double declination = AXIAL_TILT * Math.sin(longitude);
        double hourAngle = TAU * (0.5D - fractionOfDay);
        double cosineZenith = latitude.sine() * Math.sin(declination)
                + latitude.cosine() * Math.cos(declination) * Math.cos(hourAngle);
        return Math.asin(clamp(cosineZenith, -1.0D, 1.0D));
    }

    /** Prepared time path for scans that retain one instant across many observer latitudes. */
    static SolarTimeContext prepareSolarTime(double calendarDays, double daysInYear) {
        if (!Double.isFinite(calendarDays) || !Double.isFinite(daysInYear)
                || daysInYear <= 0.0D) {
            return SolarTimeContext.INVALID;
        }
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double fractionOfDay = positiveModulo(calendarDays, 1.0D);
        double longitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        double declination = AXIAL_TILT * Math.sin(longitude);
        double hourAngle = TAU * (0.5D - fractionOfDay);
        return new SolarTimeContext(Math.sin(declination), Math.cos(declination),
                Math.cos(hourAngle), true);
    }

    /**
     * Uses exactly the public solar-altitude expression while reusing the time trigonometry.
     * The multiplication and addition order intentionally matches {@link #solarElevationAt}.
     */
    static double solarElevationAt(double latitudeRadians, SolarTimeContext time) {
        if (!Double.isFinite(latitudeRadians) || time == null || !time.valid()) {
            return Double.NaN;
        }
        double cosineZenith = Math.sin(latitudeRadians) * time.sineDeclination()
                + Math.cos(latitudeRadians) * time.cosineDeclination() * time.cosineHourAngle();
        return Math.asin(clamp(cosineZenith, -1.0D, 1.0D));
    }

    public static CelestialVector eclipticToEquatorial(double longitude, double latitude, double obliquity) {
        double cosLatitude = Math.cos(latitude);
        double x = cosLatitude * Math.cos(longitude);
        double y = cosLatitude * Math.sin(longitude);
        double z = Math.sin(latitude);
        double cosObliquity = Math.cos(obliquity);
        double sinObliquity = Math.sin(obliquity);
        return normalizedVector(x,
                y * cosObliquity - z * sinObliquity,
                y * sinObliquity + z * cosObliquity);
    }

    /** Fixed-obliquity path used by the authoritative TFC sky without repeating constant trig. */
    static CelestialVector eclipticToEquatorialFixedTilt(double longitude, double latitude) {
        double cosLatitude = Math.cos(latitude);
        double x = cosLatitude * Math.cos(longitude);
        double y = cosLatitude * Math.sin(longitude);
        double z = Math.sin(latitude);
        return normalizedVector(x,
                y * AXIAL_TILT_COS - z * AXIAL_TILT_SIN,
                y * AXIAL_TILT_SIN + z * AXIAL_TILT_COS);
    }

    public static CelestialVector orbitalPosition(double radius, double orbitalDays, double inclination,
                                                   boolean retrograde, double calendarDays) {
        return orbitalPosition(radius, orbitalDays, inclination, 0.0D, retrograde, calendarDays);
    }

    /** Circular orbit in the ecliptic frame with an explicit longitude of ascending node. */
    public static CelestialVector orbitalPosition(double radius, double orbitalDays, double inclination,
                                                   double ascendingNode, boolean retrograde,
                                                   double calendarDays) {
        return orbitalPosition(radius, orbitalDays, inclination, ascendingNode, retrograde,
                calendarDays, 0.0D);
    }

    public static CelestialVector orbitalPosition(double radius, double orbitalDays, double inclination,
                                                   double ascendingNode, boolean retrograde,
                                                   double calendarDays, double phaseTurns) {
        double sign = retrograde ? -1.0D : 1.0D;
        double angle = sign * TAU * calendarDays / orbitalDays + TAU * phaseTurns;
        double nodeCos = Math.cos(ascendingNode);
        double nodeSin = Math.sin(ascendingNode);
        double cosInclination = Math.cos(inclination);
        double transverseX = -nodeSin * cosInclination;
        double transverseY = nodeCos * cosInclination;
        double transverseZ = Math.sin(inclination);
        double nodeScale = radius * Math.cos(angle);
        double transverseScale = radius * Math.sin(angle);
        return new CelestialVector(nodeCos * nodeScale + transverseX * transverseScale,
                nodeSin * nodeScale + transverseY * transverseScale,
                0.0D * nodeScale + transverseZ * transverseScale);
    }

    /**
     * Satellite orbit relative to its declared source reference plane (ecliptic, equatorial, or
     * local Laplace). The returned vector is still expressed in the common ecliptic frame, so it
     * can be added directly to the parent's heliocentric position.
     */
    public static CelestialVector satelliteOrbitalPosition(double radius, double orbitalDays,
                                                            CelestialVector referencePlaneNormal,
                                                            double relativeInclination,
                                                            double ascendingNode,
                                                            boolean retrograde,
                                                            double calendarDays) {
        return satelliteOrbitalPosition(radius, orbitalDays, referencePlaneNormal,
                relativeInclination, ascendingNode, retrograde, calendarDays, 0.0D);
    }

    public static CelestialVector satelliteOrbitalPosition(double radius, double orbitalDays,
                                                            CelestialVector referencePlaneNormal,
                                                            double relativeInclination,
                                                            double ascendingNode,
                                                            boolean retrograde,
                                                            double calendarDays,
                                                            double phaseTurns) {
        return satelliteOrbitalPosition(radius, orbitalDays,
                satelliteOrbitBasis(referencePlaneNormal, relativeInclination, ascendingNode),
                retrograde, calendarDays, phaseTurns);
    }

    static SatelliteOrbitBasis satelliteOrbitBasis(CelestialVector referencePlaneNormal,
                                                    double relativeInclination,
                                                    double ascendingNode) {
        CelestialVector normal = referencePlaneNormal.normalized();
        if (normal.lengthSquared() < 1.0E-12D) {
            throw new IllegalArgumentException("Orbit reference-plane normal must be non-zero");
        }
        // JPL's node longitudes are measured in J2000-oriented reference frames.  Use the J2000
        // equatorial north pole (expressed in our ecliptic coordinates) to establish the zero-node
        // direction; using ecliptic north here silently rotates every Laplace-plane node.
        CelestialVector reference = Math.abs(normal.dot(EQUATORIAL_NORTH_ECLIPTIC)) < 0.95D
                ? EQUATORIAL_NORTH_ECLIPTIC
                : new CelestialVector(1.0D, 0.0D, 0.0D);
        CelestialVector equatorX = cross(reference, normal).normalized();
        CelestialVector equatorY = cross(normal, equatorX).normalized();
        CelestialVector node = equatorX.scale(Math.cos(ascendingNode))
                .add(equatorY.scale(Math.sin(ascendingNode))).normalized();
        CelestialVector tiltedNormal = rotateAroundAxis(normal, node, relativeInclination).normalized();
        CelestialVector transverse = cross(tiltedNormal, node).normalized();
        return new SatelliteOrbitBasis(node, transverse);
    }

    static CelestialVector satelliteOrbitalPosition(double radius, double orbitalDays,
                                                     SatelliteOrbitBasis basis,
                                                     boolean retrograde,
                                                     double calendarDays,
                                                     double phaseTurns) {
        double sign = retrograde ? -1.0D : 1.0D;
        double angle = sign * TAU * calendarDays / orbitalDays + TAU * phaseTurns;
        double nodeScale = radius * Math.cos(angle);
        double transverseScale = radius * Math.sin(angle);
        CelestialVector node = basis.node();
        CelestialVector transverse = basis.transverse();
        return new CelestialVector(node.x() * nodeScale + transverse.x() * transverseScale,
                node.y() * nodeScale + transverse.y() * transverseScale,
                node.z() * nodeScale + transverse.z() * transverseScale);
    }

    private static CelestialVector rotateAroundAxis(CelestialVector vector, CelestialVector axis,
                                                     double angle) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        CelestialVector crossed = cross(axis, vector);
        double axialScale = axis.dot(vector) * (1.0D - cosine);
        return new CelestialVector(vector.x() * cosine + crossed.x() * sine + axis.x() * axialScale,
                vector.y() * cosine + crossed.y() * sine + axis.y() * axialScale,
                vector.z() * cosine + crossed.z() * sine + axis.z() * axialScale);
    }

    private static CelestialVector cross(CelestialVector first, CelestialVector second) {
        return new CelestialVector(first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
    }

    public static double lunarAscendingNode(double calendarDays, double daysInYear, double nodalYears) {
        double cycleDays = Math.max(1.0E-9D, daysInYear * nodalYears);
        return -TAU * positiveModulo(calendarDays / cycleDays, 1.0D);
    }

    public static double calendarDays(double calendarTicks) {
        return calendarTicks / TICKS_IN_DAY;
    }

    public static double daysInYear(int daysInMonth) {
        return Math.max(1, daysInMonth) * (double) MONTHS_IN_YEAR;
    }

    public static double calendarYears(double calendarTicks, int daysInMonth) {
        return calendarDays(calendarTicks) / daysInYear(daysInMonth);
    }

    private static CelestialVector fromRightAscension(double rightAscension,
                                                       double sineDeclination,
                                                       double cosineDeclination) {
        return new CelestialVector(cosineDeclination * Math.cos(rightAscension),
                cosineDeclination * Math.sin(rightAscension), sineDeclination);
    }

    public static CelestialVector equatorialToHorizon(CelestialVector equatorial, double latitude,
                                                       double localSiderealAngle) {
        double cosineSidereal = Math.cos(localSiderealAngle);
        double sineSidereal = Math.sin(localSiderealAngle);
        double sineLatitude = Math.sin(latitude);
        double cosineLatitude = Math.cos(latitude);
        return equatorialToHorizon(equatorial, sineLatitude, cosineLatitude,
                sineSidereal, cosineSidereal);
    }

    static CelestialVector equatorialToHorizon(CelestialVector equatorial,
                                                double sineLatitude, double cosineLatitude,
                                                double sineSidereal, double cosineSidereal) {
        return equatorialToHorizon(equatorial.x(), equatorial.y(), equatorial.z(),
                sineLatitude, cosineLatitude, sineSidereal, cosineSidereal);
    }

    /** Scalar input path for callers that do not otherwise expose the intermediate vector. */
    static CelestialVector equatorialToHorizon(double equatorialX, double equatorialY,
                                                double equatorialZ, double sineLatitude,
                                                double cosineLatitude, double sineSidereal,
                                                double cosineSidereal) {
        double meridian = equatorialX * cosineSidereal + equatorialY * sineSidereal;
        double east = -equatorialX * sineSidereal + equatorialY * cosineSidereal;
        double north = -sineLatitude * meridian + cosineLatitude * equatorialZ;
        double up = cosineLatitude * meridian + sineLatitude * equatorialZ;
        return normalizedVector(east, up, north);
    }

    /** Exact scalar form of {@code new CelestialVector(x, y, z).normalized()}. */
    private static CelestialVector normalizedVector(double x, double y, double z) {
        double lengthSquared = x * x + y * y + z * z;
        double length = Math.sqrt(lengthSquared);
        if (!(length > 1.0E-12D)) {
            return CelestialVector.ZERO;
        }
        double inverse = 1.0D / length;
        return new CelestialVector(x * inverse, y * inverse, z * inverse);
    }

    public static double angle(CelestialVector first, CelestialVector second) {
        double firstLengthSquared = first.x() * first.x() + first.y() * first.y()
                + first.z() * first.z();
        double firstLength = Math.sqrt(firstLengthSquared);
        double firstX;
        double firstY;
        double firstZ;
        if (firstLength > 1.0E-12D) {
            double firstInverse = 1.0D / firstLength;
            firstX = first.x() * firstInverse;
            firstY = first.y() * firstInverse;
            firstZ = first.z() * firstInverse;
        } else {
            firstX = 0.0D;
            firstY = 0.0D;
            firstZ = 0.0D;
        }
        double secondLengthSquared = second.x() * second.x() + second.y() * second.y()
                + second.z() * second.z();
        double secondLength = Math.sqrt(secondLengthSquared);
        double secondX;
        double secondY;
        double secondZ;
        if (secondLength > 1.0E-12D) {
            double secondInverse = 1.0D / secondLength;
            secondX = second.x() * secondInverse;
            secondY = second.y() * secondInverse;
            secondZ = second.z() * secondInverse;
        } else {
            secondX = 0.0D;
            secondY = 0.0D;
            secondZ = 0.0D;
        }
        return Math.acos(clamp(firstX * secondX + firstY * secondY + firstZ * secondZ,
                -1.0D, 1.0D));
    }

    /**
     * Maps the actual three-dimensional Sun-Moon separation to the vanilla eight phase cells.
     * The synodic progress is used only to retain the waning/waxing direction, which an unsigned
     * separation angle cannot distinguish by itself.
     */
    static int moonPhaseFromGeometry(double synodicProgress, double sunMoonSeparation) {
        double separationFraction = clamp(sunMoonSeparation / Math.PI, 0.0D, 1.0D);
        double wrappedProgress = positiveModulo(synodicProgress, 1.0D);
        double continuousPhase = wrappedProgress <= 0.5D
                ? 4.0D * (1.0D - separationFraction)
                : 4.0D * (1.0D + separationFraction);
        return Math.floorMod((int) Math.floor(continuousPhase + 0.5D), 8);
    }

    /** Fraction of the first disc covered by the second disc. */
    public static double circleCoverage(double firstRadius, double secondRadius, double separation) {
        if (firstRadius <= 0.0D || secondRadius <= 0.0D || separation >= firstRadius + secondRadius) {
            return 0.0D;
        }
        if (separation <= Math.abs(secondRadius - firstRadius)) {
            return secondRadius >= firstRadius ? 1.0D
                    : (secondRadius * secondRadius) / (firstRadius * firstRadius);
        }
        double a = firstRadius * firstRadius;
        double b = secondRadius * secondRadius;
        double x = (separation * separation + a - b) / (2.0D * separation);
        double y = Math.sqrt(Math.max(0.0D, a - x * x));
        double area = a * Math.acos(clamp(x / firstRadius, -1.0D, 1.0D))
                + b * Math.acos(clamp((separation - x) / secondRadius, -1.0D, 1.0D))
                - separation * y;
        return clamp(area / (Math.PI * a), 0.0D, 1.0D);
    }

    static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0D ? result + modulus : result;
    }

    private static double clampedMap(double value, double fromLow, double fromHigh,
                                     double toLow, double toHigh) {
        if (Math.abs(fromHigh - fromLow) < 1.0E-9D) {
            return (toLow + toHigh) * 0.5D;
        }
        return toLow + clamp((value - fromLow) / (fromHigh - fromLow), 0.0D, 1.0D) * (toHigh - toLow);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double x = clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Input(double z, double hemisphereScale, double calendarTicks, int daysInMonth,
                        double synodicDays, double anomalisticDays, double nodalYears,
                        double lunarInclination, double sunScale, double moonScale) {
        public Input(double z, double hemisphereScale, double calendarTicks, int daysInMonth) {
            this(z, hemisphereScale, calendarTicks, daysInMonth, SYNODIC_DAYS, ANOMALISTIC_DAYS,
                    NODAL_YEARS, LUNAR_INCLINATION, CelestialDiscGeometry.DEFAULT_SUN_SCALE,
                    CelestialDiscGeometry.DEFAULT_MOON_SCALE);
        }

        public Input(double z, double hemisphereScale, double calendarTicks, int daysInMonth,
                     double synodicDays, double anomalisticDays, double nodalYears,
                     double lunarInclination) {
            this(z, hemisphereScale, calendarTicks, daysInMonth, synodicDays, anomalisticDays,
                    nodalYears, lunarInclination, CelestialDiscGeometry.DEFAULT_SUN_SCALE,
                    CelestialDiscGeometry.DEFAULT_MOON_SCALE);
        }
    }

    interface EventView {
        double latitude();
        double fractionOfDay();
        double illuminatedFraction();
        int moonPhase();
        double solarEclipse();
        LunarEclipseState lunarEclipseRegion();
        double supermoon();
        double bloodMoon();
        double solarElevation();
        double moonElevation();
        double apparentDayTime();
    }

    public record Result(double latitude, double fractionOfDay, double fractionOfYear,
                         CelestialVector sunGeocentric, CelestialVector moonGeocentric,
                         CelestialVector sunDirection, CelestialVector moonDirection,
                         CelestialVector celestialNorth, double moonDistance, double moonAngularRadius,
                         double sunMoonSeparation, double illuminatedFraction, int moonPhase,
                         double solarEclipse, double physicalSolarEclipse, double lunarEclipse,
                         LunarEclipseState lunarEclipseRegion, double supermoon, double bloodMoon,
                         SolarEclipseState solarEclipseRegion,
                         double solarElevation, double moonElevation, double apparentDayTime,
                         double daylightFactor, double solarLongitude, double localSiderealAngle)
            implements EventView {
    }

    /** Thread-confined output slot for reusing full-frame horizon trigonometry in body assembly. */
    static final class HorizonProducts {
        private double sineLatitude;
        private double cosineLatitude;
        private double sineSidereal;
        private double cosineSidereal;

        private void set(double sineLatitude, double cosineLatitude,
                         double sineSidereal, double cosineSidereal) {
            this.sineLatitude = sineLatitude;
            this.cosineLatitude = cosineLatitude;
            this.sineSidereal = sineSidereal;
            this.cosineSidereal = cosineSidereal;
        }

        double sineLatitude() {
            return sineLatitude;
        }

        double cosineLatitude() {
            return cosineLatitude;
        }

        double sineSidereal() {
            return sineSidereal;
        }

        double cosineSidereal() {
            return cosineSidereal;
        }
    }

    private static final class SupermoonCache {
        private long fullMoonDayBits;
        private long anomalisticDaysBits;
        private double value;
        private boolean initialized;

        private double get(double fullMoonDay, double anomalisticDays) {
            long nextFullMoonDayBits = Double.doubleToRawLongBits(fullMoonDay);
            long nextAnomalisticDaysBits = Double.doubleToRawLongBits(anomalisticDays);
            if (!initialized || fullMoonDayBits != nextFullMoonDayBits
                    || anomalisticDaysBits != nextAnomalisticDaysBits) {
                double anomalisticProgress = positiveModulo(fullMoonDay / anomalisticDays, 1.0D);
                double distanceAtFullMoon = 1.0D - 0.07D * Math.cos(TAU * anomalisticProgress);
                double strength = clamp((1.0D - distanceAtFullMoon) / 0.07D, 0.0D, 1.0D);
                double computed = strength
                        >= first.wildfires.api.celestial.CelestialState.SUPERMOON_STRENGTH_THRESHOLD
                        ? strength : 0.0D;
                fullMoonDayBits = nextFullMoonDayBits;
                anomalisticDaysBits = nextAnomalisticDaysBits;
                value = computed;
                initialized = true;
            }
            return value;
        }
    }

    record LunarPredictionSample(LunarEclipseState lunarEclipseRegion, double supermoon) {
    }

    record DaylightSample(double solarElevation, double apparentDayTime,
                          double daylightFactor) {
    }

    record EventSample(double latitude, double fractionOfDay, double illuminatedFraction,
                       int moonPhase, double solarEclipse,
                       LunarEclipseState lunarEclipseRegion, double supermoon, double bloodMoon,
                       double solarElevation, double moonElevation, double apparentDayTime)
            implements EventView {
    }

    record DisplayEventSample(double illuminatedFraction, double solarEclipse,
                              double lunarPenumbraCoverage, double supermoon,
                              double bloodMoon, double solarElevation, double moonElevation) {
    }

    record QuarterEventSample(double illuminatedFraction, int moonPhase,
                              double solarElevation, double moonElevation) {
    }

    record SolarLatitudeContext(double sine, double cosine, boolean valid) {
        private static final SolarLatitudeContext INVALID =
                new SolarLatitudeContext(Double.NaN, Double.NaN, false);
    }

    record ObserverLatitudeContext(double latitude, double sine, double cosine) {
    }

    record SolarTimeContext(double sineDeclination, double cosineDeclination,
                            double cosineHourAngle, boolean valid) {
        private static final SolarTimeContext INVALID =
                new SolarTimeContext(Double.NaN, Double.NaN, Double.NaN, false);
    }

    record SatelliteOrbitBasis(CelestialVector node, CelestialVector transverse) {
    }
}
