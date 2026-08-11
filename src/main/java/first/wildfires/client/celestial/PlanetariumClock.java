package first.wildfires.client.celestial;

import first.wildfires.celestial.CelestialMath;

/** Pure local-day clock geometry used by the textured planetarium dial and tests. */
final class PlanetariumClock {

    private PlanetariumClock() {
    }

    static Schedule schedule(double latitudeRadians, double fractionOfYear) {
        if (!Double.isFinite(latitudeRadians) || !Double.isFinite(fractionOfYear)) {
            return Schedule.EQUINOX;
        }
        double latitude = clamp(latitudeRadians, -Math.PI * 0.5D, Math.PI * 0.5D);
        double year = fractionOfYear - Math.floor(fractionOfYear);
        double solarLongitude = CelestialMath.TAU
                * positiveModulo(284.0D / 365.0D + year, 1.0D);
        double declination = CelestialMath.AXIAL_TILT * Math.sin(solarLongitude);
        double horizonCosine = -Math.tan(latitude) * Math.tan(declination);
        if (Double.isNaN(horizonCosine)) {
            horizonCosine = 0.0D;
        }
        if (horizonCosine <= -1.0D) {
            return new Schedule(0.0D, 1.0D, 1.0D, true, false);
        }
        if (horizonCosine >= 1.0D) {
            return new Schedule(0.0D, 0.0D, 0.0D, false, true);
        }
        double halfDayAngle = Math.acos(clamp(horizonCosine, -1.0D, 1.0D));
        double sunrise = 0.5D - halfDayAngle / CelestialMath.TAU;
        double sunset = 0.5D + halfDayAngle / CelestialMath.TAU;
        return new Schedule(sunrise, sunset, sunset - sunrise, false, false);
    }

    static double pointerFraction(double fractionOfDay) {
        return Double.isFinite(fractionOfDay) ? positiveModulo(fractionOfDay, 1.0D) : 0.0D;
    }

    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0D ? result + modulus : result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record Schedule(double sunriseFraction, double sunsetFraction, double dayFraction,
                    boolean polarDay, boolean polarNight) {
        private static final Schedule EQUINOX = new Schedule(0.25D, 0.75D, 0.5D,
                false, false);
    }
}
