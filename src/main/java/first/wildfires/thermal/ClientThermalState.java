package first.wildfires.thermal;

/** Smoothed client-side copy of the three server-authoritative local thermal values. */
public final class ClientThermalState {

    private static final int SMOOTHING_TICKS = 5;
    private static float airTemperature;
    private static float radiationOffset;
    private static float effectiveOffset;
    private static float targetAirTemperature;
    private static float targetRadiationOffset;
    private static float targetEffectiveOffset;
    private static int smoothingTicksRemaining;

    private ClientThermalState() {
    }

    public static float getAirTemperature() {
        return airTemperature;
    }

    public static float getRadiationOffset() {
        return radiationOffset;
    }

    public static float getEffectiveOffset() {
        return effectiveOffset;
    }

    public static void setTargets(float air, float radiation, float effective) {
        targetAirTemperature = air;
        targetRadiationOffset = radiation;
        targetEffectiveOffset = effective;
        smoothingTicksRemaining = SMOOTHING_TICKS;
    }

    public static void tick() {
        if (smoothingTicksRemaining <= 0) {
            return;
        }
        float fraction = 1.0F / smoothingTicksRemaining;
        airTemperature += (targetAirTemperature - airTemperature) * fraction;
        radiationOffset += (targetRadiationOffset - radiationOffset) * fraction;
        effectiveOffset += (targetEffectiveOffset - effectiveOffset) * fraction;
        smoothingTicksRemaining--;
    }

    public static void clear() {
        airTemperature = 0.0F;
        radiationOffset = 0.0F;
        effectiveOffset = 0.0F;
        targetAirTemperature = 0.0F;
        targetRadiationOffset = 0.0F;
        targetEffectiveOffset = 0.0F;
        smoothingTicksRemaining = 0;
    }
}
