package first.wildfires.celestial;

/** Pure, deterministic rules shared by blood-moon gameplay and regression tests. */
public final class CelestialGameplayRules {

    /** TFCCaelum enables blood-moon spawn rules only when its event strength is strictly above 0.2. */
    public static final double ACTIVE_THRESHOLD = 0.2D;

    private CelestialGameplayRules() {
    }

    public static double visibleBloodMoon(double rawIntensity, double moonElevationRadians) {
        if (!Double.isFinite(rawIntensity) || !Double.isFinite(moonElevationRadians)
                || moonElevationRadians <= 0.0D || rawIntensity <= ACTIVE_THRESHOLD) {
            return 0.0D;
        }
        return clamp(rawIntensity, 0.0D, 1.0D);
    }

    public static int localMobCapLimit(int vanillaLimit, double intensity, double configuredMultiplier) {
        if (vanillaLimit <= 0) {
            return vanillaLimit;
        }
        double safeIntensity = clamp(intensity, 0.0D, 1.0D);
        double safeMultiplier = Math.max(1.0D, configuredMultiplier);
        double scale = 1.0D + safeIntensity * (safeMultiplier - 1.0D);
        return Math.max(vanillaLimit, (int) Math.ceil(vanillaLimit * scale));
    }

    public static int unluckAmplifier(double intensity) {
        return Math.max(0, (int) Math.round(2.0D * clamp(intensity, 0.0D, 1.0D)));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
