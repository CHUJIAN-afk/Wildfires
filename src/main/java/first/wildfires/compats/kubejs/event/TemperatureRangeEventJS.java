package first.wildfires.compats.kubejs.event;

import dev.latvian.mods.kubejs.event.EventJS;
import first.wildfires.compats.legendarysurvivaloverhaul.TemperatureRangeManager;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureEnum;

import java.util.Locale;
import java.util.Objects;

/** Mutable five-band LSO range table used only during KubeJS startup modification. */
public final class TemperatureRangeEventJS extends EventJS {

    private final int[] bounds;

    public TemperatureRangeEventJS(int[] defaults) {
        if (defaults.length != TemperatureRangeManager.BOUND_COUNT) {
            throw new IllegalArgumentException("Invalid default LSO temperature range count");
        }
        bounds = defaults.clone();
    }

    /** Sets one of FROSTBITE, COLD, NORMAL, HOT, or HEAT_STROKE. Names are case-insensitive. */
    public void range(String band, int lowerBound, int upperBound) {
        TemperatureEnum value = resolveBand(band);
        int offset = value.ordinal() * 2;
        bounds[offset] = lowerBound;
        bounds[offset + 1] = upperBound;
    }

    public int getLowerBound(String band) {
        return bounds[resolveOffset(band)];
    }

    public int getUpperBound(String band) {
        return bounds[resolveOffset(band) + 1];
    }

    public int[] snapshot() {
        return bounds.clone();
    }

    private static int resolveOffset(String band) {
        return resolveBand(band).ordinal() * 2;
    }

    private static TemperatureEnum resolveBand(String band) {
        String name = Objects.requireNonNull(band, "LSO temperature band cannot be null");
        try {
            return TemperatureEnum.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown LSO temperature band: " + band, exception);
        }
    }
}
