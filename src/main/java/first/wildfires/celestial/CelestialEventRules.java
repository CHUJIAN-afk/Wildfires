package first.wildfires.celestial;

/** Pure event rules shared by server-side calendar targeting and client renderers. */
public final class CelestialEventRules {

    private static final double DEG_TO_RAD = Math.PI / 180.0D;

    private CelestialEventRules() {
    }

    public static double auroraProbability(double absoluteLatitudeDegrees) {
        if (!Double.isFinite(absoluteLatitudeDegrees) || absoluteLatitudeDegrees < 50.0D) {
            return 0.0D;
        }
        if (absoluteLatitudeDegrees >= 65.0D) {
            return 0.42D;
        }
        return 0.02D + (absoluteLatitudeDegrees - 50.0D) / 15.0D * 0.18D;
    }

    public static long auroraEventKey(long calendarTicks, double latitudeRadians) {
        long night = Math.floorDiv(calendarTicks, (long) CelestialMath.TICKS_IN_DAY);
        int latitudeBand = (int) Math.floor(Math.toDegrees(latitudeRadians) / 5.0D);
        return mix64(night ^ (long) latitudeBand * 0x9E3779B97F4A7C15L);
    }

    public static double auroraRoll(long eventKey) {
        return (eventKey >>> 11) * 0x1.0p-53;
    }

    public static boolean auroraVisible(boolean legacyGlobal, boolean disabled, int bands,
                                        double latitudeRadians, double sunAltitudeRadians,
                                        double deterministicRoll) {
        if (disabled || bands <= 0 || !Double.isFinite(latitudeRadians)
                || !Double.isFinite(sunAltitudeRadians) || !Double.isFinite(deterministicRoll)) {
            return false;
        }
        if (legacyGlobal) {
            return true;
        }
        double absoluteLatitude = Math.abs(Math.toDegrees(latitudeRadians));
        return sunAltitudeRadians < -6.0D * DEG_TO_RAD
                && deterministicRoll < auroraProbability(absoluteLatitude);
    }

    public static boolean startsRainbow(float rainBefore, float currentRain, float rainAfter,
                                        double apparentDayTime, double sunAltitudeRadians) {
        return Float.isFinite(rainBefore) && Float.isFinite(currentRain) && Float.isFinite(rainAfter)
                && currentRain < 0.5F && rainBefore > currentRain && rainAfter < currentRain
                && apparentDayTime <= 9000.0D / CelestialMath.TICKS_IN_DAY
                && sunAltitudeRadians > 0.0D;
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    public record RainSample(float before, float current, float after) {

        public static final RainSample DRY = new RainSample(0.0F, 0.0F, 0.0F);
    }
}
