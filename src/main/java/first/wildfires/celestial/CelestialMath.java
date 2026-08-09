package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialVector;

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
    public static final double EARTH_UMBRA_RADIUS_AT_MOON = 0.72D * DEG_TO_RAD;
    public static final double MOON_MEAN_DISTANCE_MILLION_KM = 0.3844D;

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
        CelestialVector moonDirection = equatorialToHorizon(moonEquatorial, latitude, localSiderealAngle);

        double anomalisticProgress = positiveModulo(calendarDays / input.anomalisticDays, 1.0D);
        double moonDistance = 1.0D - 0.07D * Math.cos(TAU * anomalisticProgress);
        double moonRadius = MOON_ANGULAR_RADIUS / moonDistance;
        double sunMoonSeparation = angle(sunDirection, moonDirection);
        double solarEclipse = circleCoverage(SUN_ANGULAR_RADIUS, moonRadius, sunMoonSeparation);
        double lunarSeparation = angle(sunEquatorial.negated(), moonEquatorial);
        double lunarEclipse = circleCoverage(moonRadius, EARTH_UMBRA_RADIUS_AT_MOON, lunarSeparation);
        double illuminated = clamp((1.0D - sunDirection.dot(moonDirection)) * 0.5D, 0.0D, 1.0D);
        int moonPhase = moonPhaseFromGeometry(synodicProgress, sunMoonSeparation);
        double supermoon = clamp((1.0D - moonDistance) / 0.07D, 0.0D, 1.0D);
        double solarElevation = Math.asin(clamp(sunDirection.y(), -1.0D, 1.0D));
        double moonElevation = Math.asin(clamp(moonDirection.y(), -1.0D, 1.0D));
        double daylight = smoothstep(-6.0D * DEG_TO_RAD, 0.0D, solarElevation);
        double apparentDayTime = sunBasedDayTime(input, fractionOfYear, fractionOfDay) / 24000.0D;
        CelestialVector celestialNorth = equatorialToHorizon(new CelestialVector(0.0D, 0.0D, 1.0D),
                latitude, localSiderealAngle);

        return new Result(latitude, fractionOfDay, fractionOfYear, sunEquatorial, moonEquatorial,
                sunDirection, moonDirection, celestialNorth, moonDistance, moonRadius, sunMoonSeparation,
                illuminated, moonPhase, solarEclipse, lunarEclipse, supermoon, lunarEclipse,
                solarElevation, moonElevation, apparentDayTime, daylight, solarLongitude, localSiderealAngle);
    }

    private static int sunBasedDayTime(Input input, double fractionOfYear, double fractionOfDay) {
        double zenith = Math.PI * 0.5D - calculateSunElevation(input.z, input.hemisphereScale,
                fractionOfYear, fractionOfDay);
        if (fractionOfDay < 0.5D) {
            if (zenith > Math.PI * 0.5D) {
                double midnight = Math.PI * 0.5D - calculateSunElevation(input.z, input.hemisphereScale,
                        fractionOfYear, 0.0D);
                return (int) clampedMap(zenith, midnight, Math.PI * 0.5D, 18000.0D, 24000.0D);
            }
            double noon = Math.PI * 0.5D - calculateSunElevation(input.z, input.hemisphereScale,
                    fractionOfYear, 0.5D);
            return (int) clampedMap(zenith, Math.PI * 0.5D, noon, 0.0D, 6000.0D);
        }
        if (zenith < Math.PI * 0.5D) {
            double noon = Math.PI * 0.5D - calculateSunElevation(input.z, input.hemisphereScale,
                    fractionOfYear, 0.5D);
            return (int) clampedMap(zenith, noon, Math.PI * 0.5D, 6000.0D, 12000.0D);
        }
        double midnight = Math.PI * 0.5D - calculateSunElevation(input.z, input.hemisphereScale,
                fractionOfYear, 1.0D);
        return (int) clampedMap(zenith, Math.PI * 0.5D, midnight, 12000.0D, 18000.0D);
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

    private static double positiveModulo(double value, double modulus) {
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
                        double lunarInclination) {
        public Input(double z, double hemisphereScale, double calendarTicks, int daysInMonth) {
            this(z, hemisphereScale, calendarTicks, daysInMonth, SYNODIC_DAYS, ANOMALISTIC_DAYS,
                    NODAL_YEARS, LUNAR_INCLINATION);
        }
    }

    public record Result(double latitude, double fractionOfDay, double fractionOfYear,
                         CelestialVector sunGeocentric, CelestialVector moonGeocentric,
                         CelestialVector sunDirection, CelestialVector moonDirection,
                         CelestialVector celestialNorth, double moonDistance, double moonAngularRadius,
                         double sunMoonSeparation, double illuminatedFraction, int moonPhase,
                         double solarEclipse, double lunarEclipse, double supermoon, double bloodMoon,
                         double solarElevation, double moonElevation, double apparentDayTime,
                         double daylightFactor, double solarLongitude, double localSiderealAngle) {
    }
}
