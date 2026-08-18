package first.wildfires.compats.legendarysurvivaloverhaul;

import com.mojang.logging.LogUtils;
import first.wildfires.api.customEvent.TemperatureEnumModifyEvent;
import first.wildfires.compats.kubejs.event.TemperatureRangeEventJS;
import first.wildfires.compats.kubejs.event.TemperatureRangeEvents;
import first.wildfires.mixin.legendarysurvivaloverhaul.TemperatureEnumAccessor;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureEnum;

import java.util.Arrays;

/** Applies validated LSO temperature bands at KubeJS' real startup modification boundary. */
public final class TemperatureRangeManager {

    public static final int BOUND_COUNT = TemperatureEnum.values().length * 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int[] DEFAULT_BOUNDS = {
            0, 10,
            10, 16,
            16, 24,
            24, 30,
            30, 40
    };

    private TemperatureRangeManager() {
    }

    /** Resets stale state, posts the KubeJS table and legacy Forge events, then validates atomically. */
    public static synchronized void postKubeJsModificationEvents() {
        applyUnchecked(DEFAULT_BOUNDS);
        TemperatureRangeEventJS kubeJsEvent = new TemperatureRangeEventJS(DEFAULT_BOUNDS);
        TemperatureRangeEvents.MODIFY.post(kubeJsEvent);
        int[] kubeJsBounds = kubeJsEvent.snapshot();
        if (!isValidBounds(kubeJsBounds)) {
            LOGGER.error("Rejected invalid KubeJS LSO temperature ranges {}; restored defaults {}",
                    Arrays.toString(kubeJsBounds), Arrays.toString(DEFAULT_BOUNDS));
            kubeJsBounds = DEFAULT_BOUNDS.clone();
        }
        applyUnchecked(kubeJsBounds);

        int[] proposed = new int[BOUND_COUNT];
        TemperatureEnum[] values = TemperatureEnum.values();
        for (int index = 0; index < values.length; index++) {
            TemperatureEnumModifyEvent event = new TemperatureEnumModifyEvent(values[index]);
            MinecraftForge.EVENT_BUS.post(event);
            proposed[index * 2] = event.getLowerBound();
            proposed[index * 2 + 1] = event.getUpperBound();
        }
        if (!isValidBounds(proposed)) {
            applyUnchecked(DEFAULT_BOUNDS);
            LOGGER.error("Rejected invalid final LSO temperature ranges {}; restored defaults {}",
                    Arrays.toString(proposed), Arrays.toString(DEFAULT_BOUNDS));
            return;
        }
        applyUnchecked(proposed);
        LOGGER.info("Applied KubeJS LSO temperature ranges {}", Arrays.toString(proposed));
    }

    /** Returns the authoritative ten-value lower/upper snapshot in enum order. */
    public static synchronized int[] snapshot() {
        TemperatureEnum[] values = TemperatureEnum.values();
        int[] bounds = new int[BOUND_COUNT];
        for (int index = 0; index < values.length; index++) {
            bounds[index * 2] = values[index].getLowerBound();
            bounds[index * 2 + 1] = values[index].getUpperBound();
        }
        return bounds;
    }

    /** Applies a server-authoritative snapshot on the logical client. Invalid wire data is ignored. */
    public static synchronized void applySynchronizedBounds(int[] bounds) {
        if (!isValidBounds(bounds)) {
            LOGGER.error("Ignored invalid synchronized LSO temperature ranges {}", Arrays.toString(bounds));
            return;
        }
        applyUnchecked(bounds);
    }

    static boolean isValidBounds(int[] bounds) {
        if (bounds == null || bounds.length != BOUND_COUNT || bounds[0] != 0) {
            return false;
        }
        long totalSpan = (long) bounds[BOUND_COUNT - 1] - bounds[0];
        if (totalSpan <= 0L || totalSpan > Integer.MAX_VALUE) {
            return false;
        }
        for (int index = 0; index < BOUND_COUNT; index += 2) {
            if (bounds[index] >= bounds[index + 1]) {
                return false;
            }
            long middleSum = (long) bounds[index] + bounds[index + 1];
            if (middleSum < Integer.MIN_VALUE || middleSum > Integer.MAX_VALUE) {
                return false;
            }
            if (index + 2 < BOUND_COUNT && bounds[index + 1] != bounds[index + 2]) {
                return false;
            }
        }
        return true;
    }

    private static void applyUnchecked(int[] bounds) {
        TemperatureEnum[] values = TemperatureEnum.values();
        for (int index = 0; index < values.length; index++) {
            TemperatureEnumAccessor accessor = (TemperatureEnumAccessor) (Object) values[index];
            accessor.setLowerBound(bounds[index * 2]);
            accessor.setUpperBound(bounds[index * 2 + 1]);
        }
    }
}
