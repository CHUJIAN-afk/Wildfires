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
    public static final double LUNAR_INCLINATION = 5.14D * DEG_TO_RAD;
    public static final double SYNODIC_DAYS = 16.13D;
    public static final double ANOMALISTIC_DAYS = SYNODIC_DAYS * 27.55455D / 29.530588D;
    public static final double NODAL_YEARS = 18.6D;
    public static final double SUN_ANGULAR_RADIUS = 0.2666D * DEG_TO_RAD;
    public static final double MOON_ANGULAR_RADIUS = 0.2725D * DEG_TO_RAD;
    public static final double MOON_MEAN_DISTANCE_MILLION_KM = 0.3844D;
    public static final double SUPERMOON_FULL_MOON_HALF_WINDOW_DAYS = 0.5D;

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
        double daysInYear = daysInYear(input.daysInMonth);
        double calendarDays = calendarDays(input.calendarTicks);
        double fractionOfDay = positiveModulo(input.calendarTicks, TICKS_IN_DAY) / TICKS_IN_DAY;
        double fractionOfYear = positiveModulo(calendarDays, daysInYear) / daysInYear;
        double latitude = latitude(input.z, input.hemisphereScale);

        double solarLongitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        CelestialVector rawSun = eclipticToEquatorial(solarLongitude, 0.0D, AXIAL_TILT);
        double declination = AXIAL_TILT * Math.sin(solarLongitude);
        double sunRightAscension = Math.atan2(rawSun.y(), rawSun.x());
        CelestialVector sunEquatorial = fromRightAscension(sunRightAscension, declination);
        double localSiderealAngle = sunRightAscension + TAU * (fractionOfDay - 0.5D);
        CelestialVector sunDirection = equatorialToHorizon(sunEquatorial, latitude, localSiderealAngle);

        double synodicProgress = positiveModulo(calendarDays / input.synodicDays, 1.0D);
        double elongation = Math.PI + TAU * synodicProgress;
        double moonLongitude = solarLongitude + elongation;
        double ascendingNode = lunarAscendingNode(calendarDays, daysInYear, input.nodalYears);
        double moonLatitude = Math.asin(Math.sin(input.lunarInclination)
                * Math.sin(moonLongitude - ascendingNode));
        CelestialVector moonEquatorial = eclipticToEquatorial(moonLongitude, moonLatitude, AXIAL_TILT);
        CelestialVector rawMoonDirection = equatorialToHorizon(moonEquatorial, latitude, localSiderealAngle);

        double anomalisticProgress = positiveModulo(calendarDays / input.anomalisticDays, 1.0D);
        double moonDistance = 1.0D - 0.07D * Math.cos(TAU * anomalisticProgress);
        double moonRadius = MOON_ANGULAR_RADIUS / moonDistance;
        double sunMoonSeparation = angle(sunDirection, rawMoonDirection);
        CelestialVector celestialNorth = equatorialToHorizon(new CelestialVector(0.0D, 0.0D, 1.0D),
                latitude, localSiderealAngle);
        double physicalSolarEclipse = circleCoverage(SUN_ANGULAR_RADIUS, moonRadius, sunMoonSeparation);
        double sunPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(
                CelestialDiscGeometry.sunBodyHalfSize(input.sunScale));
        double moonBodyHalfSize = CelestialDiscGeometry.moonBodyHalfSize(input.moonScale, moonDistance);
        double moonPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize,
                CelestialDiscGeometry.PIXEL_COVER_RADIUS);
        SolarEclipseRegion.Event solarEvent = SolarEclipseRegion.eventFor(calendarDays, daysInYear,
                input.synodicDays, input.nodalYears, input.lunarInclination);
        SolarEclipseRegion.Projection solarProjection = SolarEclipseRegion.project(solarEvent,
                calendarDays, latitude, sunDirection, rawMoonDirection, celestialNorth,
                sunPixelHalfTangent, moonPixelHalfTangent, input.synodicDays);
        CelestialVector moonDirection = solarProjection.moonDirection();
        double solarEclipse = solarProjection.coverage();
        double lunarPixelHalfTangent = CelestialDiscGeometry.tangentHalfExtent(moonBodyHalfSize);
        LunarEclipseRegion.Event lunarEvent = LunarEclipseRegion.eventFor(calendarDays, daysInYear,
                input.synodicDays, input.nodalYears, input.lunarInclination);
        LunarEclipseRegion.Projection lunarProjection = LunarEclipseRegion.project(lunarEvent,
                rawMoonDirection, sunDirection, celestialNorth,
                lunarPixelHalfTangent, lunarPixelHalfTangent);
        double lunarEclipse = lunarProjection.umbraCoverage();
        // Phase illumination remains a property of the physical three-dimensional orbit.
        // The regional apparent direction may move a non-eclipse conjunction clear of the
        // enlarged pixel Sun, but it must not erase the 16.13-day new/full Moon cycle.
        double illuminated = clamp((1.0D - sunDirection.dot(rawMoonDirection)) * 0.5D, 0.0D, 1.0D);
        int moonPhase = moonPhaseFromGeometry(synodicProgress, sunMoonSeparation);
        double supermoon = supermoonAtFullMoon(calendarDays, input.synodicDays,
                input.anomalisticDays);
        double solarElevation = Math.asin(clamp(sunDirection.y(), -1.0D, 1.0D));
        double moonElevation = Math.asin(clamp(moonDirection.y(), -1.0D, 1.0D));
        double daylight = smoothstep(-6.0D * DEG_TO_RAD, 0.0D, solarElevation);
        double apparentDayTime = sunBasedDayTime(input, fractionOfYear, fractionOfDay) / 24000.0D;
        return new Result(latitude, fractionOfDay, fractionOfYear, sunEquatorial, moonEquatorial,
                sunDirection, moonDirection, celestialNorth, moonDistance, moonRadius, sunMoonSeparation,
                illuminated, moonPhase, solarEclipse, physicalSolarEclipse, lunarEclipse,
                lunarProjection.state(), supermoon, lunarEclipse,
                solarProjection.state(), solarElevation, moonElevation, apparentDayTime, daylight,
                solarLongitude, localSiderealAngle);
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
        double anomalisticProgress = positiveModulo(fullMoonDay / anomalisticDays, 1.0D);
        double distanceAtFullMoon = 1.0D - 0.07D * Math.cos(TAU * anomalisticProgress);
        double strength = clamp((1.0D - distanceAtFullMoon) / 0.07D, 0.0D, 1.0D);
        return strength >= first.wildfires.api.celestial.CelestialState.SUPERMOON_STRENGTH_THRESHOLD
                ? strength : 0.0D;
    }

    private static double sunBasedDayTime(Input input, double fractionOfYear, double fractionOfDay) {
        double currentElevation = calculateSunElevation(input.z, input.hemisphereScale,
                fractionOfYear, fractionOfDay);
        double midnightElevation = calculateSunElevation(input.z, input.hemisphereScale,
                fractionOfYear, 0.0D);
        double noonElevation = calculateSunElevation(input.z, input.hemisphereScale,
                fractionOfYear, 0.5D);
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

    private static double calculateSunElevation(double z, double scale, double fractionOfYear, double fractionOfDay) {
        double lat = latitude(z, scale);
        double longitude = TAU * positiveModulo(284.0D / 365.0D + fractionOfYear, 1.0D);
        double declination = AXIAL_TILT * Math.sin(longitude);
        double hourAngle = TAU * (0.5D - fractionOfDay);
        double cosineZenith = Math.sin(lat) * Math.sin(declination)
                + Math.cos(lat) * Math.cos(declination) * Math.cos(hourAngle);
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

    public static CelestialVector eclipticToEquatorial(double longitude, double latitude, double obliquity) {
        double cosLatitude = Math.cos(latitude);
        double x = cosLatitude * Math.cos(longitude);
        double y = cosLatitude * Math.sin(longitude);
        double z = Math.sin(latitude);
        return new CelestialVector(x,
                y * Math.cos(obliquity) - z * Math.sin(obliquity),
                y * Math.sin(obliquity) + z * Math.cos(obliquity)).normalized();
    }

    public static CelestialVector orbitalPosition(double radius, double orbitalDays, double inclination,
                                                   boolean retrograde, double calendarDays) {
        double sign = retrograde ? -1.0D : 1.0D;
        double angle = sign * TAU * calendarDays / orbitalDays;
        double x = radius * Math.cos(angle);
        double flat = radius * Math.sin(angle);
        return new CelestialVector(x, flat * Math.cos(inclination), flat * Math.sin(inclination));
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

    private static CelestialVector fromRightAscension(double rightAscension, double declination) {
        double cos = Math.cos(declination);
        return new CelestialVector(cos * Math.cos(rightAscension), cos * Math.sin(rightAscension),
                Math.sin(declination));
    }

    public static CelestialVector equatorialToHorizon(CelestialVector equatorial, double latitude,
                                                       double localSiderealAngle) {
        double meridian = equatorial.x() * Math.cos(localSiderealAngle)
                + equatorial.y() * Math.sin(localSiderealAngle);
        double east = -equatorial.x() * Math.sin(localSiderealAngle)
                + equatorial.y() * Math.cos(localSiderealAngle);
        double north = -Math.sin(latitude) * meridian + Math.cos(latitude) * equatorial.z();
        double up = Math.cos(latitude) * meridian + Math.sin(latitude) * equatorial.z();
        return new CelestialVector(east, up, north).normalized();
    }

    public static double angle(CelestialVector first, CelestialVector second) {
        return Math.acos(clamp(first.normalized().dot(second.normalized()), -1.0D, 1.0D));
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

    public record Result(double latitude, double fractionOfDay, double fractionOfYear,
                         CelestialVector sunGeocentric, CelestialVector moonGeocentric,
                         CelestialVector sunDirection, CelestialVector moonDirection,
                         CelestialVector celestialNorth, double moonDistance, double moonAngularRadius,
                         double sunMoonSeparation, double illuminatedFraction, int moonPhase,
                         double solarEclipse, double physicalSolarEclipse, double lunarEclipse,
                         LunarEclipseState lunarEclipseRegion, double supermoon, double bloodMoon,
                         SolarEclipseState solarEclipseRegion,
                         double solarElevation, double moonElevation, double apparentDayTime,
                         double daylightFactor, double solarLongitude, double localSiderealAngle) {
    }
}
