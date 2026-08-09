package first.wildfires.celestial;

/** Server-authoritative parameters used by the common celestial model. */
public record CelestialRuntimeSettings(double synodicDays,
                                       double anomalisticDays,
                                       double nodalYears,
                                       double lunarInclinationRadians,
                                       boolean bloodMoonSurfaceMonsters,
                                       double bloodMoonSpawnMultiplier,
                                       LunarPeriodPreset lunarPeriodPreset,
                                       CelestialPlanetSettings planetSettings) {

    public CelestialRuntimeSettings {
        if (lunarPeriodPreset == null || planetSettings == null) {
            throw new IllegalArgumentException("Celestial runtime settings cannot contain null values");
        }
    }

    public enum LunarPeriodPreset {
        /** The Wildfires model selected for new worlds. */
        UNIFIED_16_13,
        /** Reproduces TFCCaelum's mixed orbit/supermoon period semantics. */
        LEGACY_TFCCAELUM,
        /** Uses the explicit numeric period fields. */
        CUSTOM
    }

    public static final CelestialRuntimeSettings DEFAULT = new CelestialRuntimeSettings(
            CelestialMath.SYNODIC_DAYS,
            CelestialMath.ANOMALISTIC_DAYS,
            CelestialMath.NODAL_YEARS,
            CelestialMath.LUNAR_INCLINATION,
            true,
            3.0D,
            LunarPeriodPreset.UNIFIED_16_13,
            CelestialPlanetSettings.DEFAULT
    );

    public double resolvedSynodicDays(int calendarDaysInMonth) {
        return switch (lunarPeriodPreset) {
            case UNIFIED_16_13 -> CelestialMath.SYNODIC_DAYS;
            case LEGACY_TFCCAELUM -> Math.max(1, calendarDaysInMonth) * 29.530588D / 30.436875D;
            case CUSTOM -> synodicDays;
        };
    }

    public double resolvedAnomalisticDays(int calendarDaysInMonth) {
        return switch (lunarPeriodPreset) {
            case UNIFIED_16_13 -> CelestialMath.ANOMALISTIC_DAYS;
            case LEGACY_TFCCAELUM -> 29.530588D;
            case CUSTOM -> anomalisticDays;
        };
    }
}
