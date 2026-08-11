package first.wildfires.client.celestial;

import first.wildfires.api.celestial.CelestialApi;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.celestial.CelestialRuntimeSettings;
import first.wildfires.celestial.CelestialSettingsCache;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import java.util.Optional;
import java.util.function.Supplier;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Exact single-state memoization for repeated visual queries during one rendered frame. */
public final class CelestialClientStateCache {

    private static final SingleEntryCache<CelestialState> CACHE = new SingleEntryCache<>();

    private CelestialClientStateCache() {
    }

    public static Optional<CelestialState> state(ClientLevel level, Vec3 observer, float partialTick) {
        long calendarTick = Calendars.get(level).getCalendarTicks();
        CelestialRuntimeSettings settings = CelestialSettingsCache.current();
        CelestialState state = CACHE.get(level, calendarTick, level.getGameTime(), partialTick,
                level.getRainLevel(partialTick), TfcCalendarRateController.clientMultiplier(), observer, settings,
                () -> CelestialApi.state(level, observer, partialTick).orElse(null));
        return Optional.ofNullable(state);
    }

    public static void reset() {
        CACHE.clear();
    }

    static final class SingleEntryCache<T> {
        private Object levelIdentity;
        private long calendarTick;
        private long gameTick;
        private int partialTickBits;
        private int rainLevelBits;
        private long calendarRateBits;
        private long observerXBits;
        private long observerYBits;
        private long observerZBits;
        private Object settingsIdentity;
        private boolean initialized;
        private T value;

        T get(Object level, long tick, long worldTick, float partialTick, float rainLevel,
              double calendarRate,
              Vec3 observer, Object settings,
              Supplier<T> factory) {
            int partialBits = Float.floatToRawIntBits(partialTick);
            int rainBits = Float.floatToRawIntBits(rainLevel);
            long rateBits = Double.doubleToRawLongBits(calendarRate);
            long xBits = Double.doubleToRawLongBits(observer.x);
            long yBits = Double.doubleToRawLongBits(observer.y);
            long zBits = Double.doubleToRawLongBits(observer.z);
            if (!initialized || levelIdentity != level || calendarTick != tick || gameTick != worldTick
                    || partialTickBits != partialBits || rainLevelBits != rainBits || observerXBits != xBits
                    || observerYBits != yBits || observerZBits != zBits
                    || calendarRateBits != rateBits
                    || settingsIdentity != settings) {
                levelIdentity = level;
                calendarTick = tick;
                gameTick = worldTick;
                partialTickBits = partialBits;
                rainLevelBits = rainBits;
                calendarRateBits = rateBits;
                observerXBits = xBits;
                observerYBits = yBits;
                observerZBits = zBits;
                settingsIdentity = settings;
                value = factory.get();
                initialized = true;
            }
            return value;
        }

        void clear() {
            initialized = false;
            levelIdentity = null;
            settingsIdentity = null;
            value = null;
        }
    }
}
