package first.wildfires.client.celestial;

import first.wildfires.api.celestial.CelestialApi;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.celestial.CelestialRuntimeSettings;
import first.wildfires.celestial.CelestialSettingsCache;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import first.wildfires.client.space.SpaceClientState;
import first.wildfires.space.celestial.ExistingCelestialEphemeris;
import java.util.Objects;
import java.util.Optional;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Exact single-state memoization for repeated visual queries during one rendered frame. */
public final class CelestialClientStateCache {

    private static final SingleEntryCache<CelestialState> CACHE = new SingleEntryCache<>(
            (level, calendarTick, gameTick, partialTick, rainLevel, calendarRate,
             observer, settings, context) -> CelestialApi.state(
                    (ClientLevel) level, observer, partialTick).orElse(null));
    private static final SingleEntryCache<CelestialState> UNIVERSE_EPHEMERIS_CACHE =
            new SingleEntryCache<>((level, calendarTick, gameTick, partialTick, rainLevel,
                                    calendarRate, observer, settings, context) ->
                    ExistingCelestialEphemeris.INSTANCE.state(
                            (ClientLevel) level, observer, partialTick));

    private CelestialClientStateCache() {
    }

    public static Optional<CelestialState> state(ClientLevel level, Vec3 observer, float partialTick) {
        return Optional.ofNullable(stateOrNull(level, observer, partialTick));
    }

    /** Allocation-free internal view for render hot paths; the public Optional contract remains. */
    public static CelestialState stateOrNull(ClientLevel level, Vec3 observer, float partialTick) {
        long calendarTick = Calendars.get(level).getCalendarTicks();
        CelestialRuntimeSettings settings = CelestialSettingsCache.current();
        return CACHE.get(level, calendarTick, level.getGameTime(), partialTick,
                level.getRainLevel(partialTick), TfcCalendarRateController.clientMultiplier(), observer, settings,
                SpaceClientState.cacheIdentity());
    }

    /**
     * Supplies the common universe ephemeris for an explicitly bound surface even when that
     * existing dimension has no local sky provider. A dimension-specific provider always wins;
     * this fallback is only for the capsule's body-driven ascent presentation.
     */
    public static CelestialState stateForBoundSurfaceAscent(ClientLevel level, Vec3 observer,
                                                            float partialTick) {
        CelestialState registered = stateOrNull(level, observer, partialTick);
        if (registered != null) return registered;
        long calendarTick = Calendars.get(level).getCalendarTicks();
        CelestialRuntimeSettings settings = CelestialSettingsCache.current();
        return UNIVERSE_EPHEMERIS_CACHE.get(level, calendarTick, level.getGameTime(), partialTick,
                level.getRainLevel(partialTick), TfcCalendarRateController.clientMultiplier(), observer, settings,
                SpaceClientState.cacheIdentity());
    }

    public static void reset() {
        CACHE.clear();
        UNIVERSE_EPHEMERIS_CACHE.clear();
    }

    static final class SingleEntryCache<T> {
        private final Factory<T> factory;
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
        private Object contextIdentity;
        private boolean initialized;
        private T value;

        SingleEntryCache(Factory<T> factory) {
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        T get(Object level, long tick, long worldTick, float partialTick, float rainLevel,
              double calendarRate,
              Vec3 observer, Object settings) {
            return get(level, tick, worldTick, partialTick, rainLevel, calendarRate,
                    observer, settings, null);
        }

        T get(Object level, long tick, long worldTick, float partialTick, float rainLevel,
              double calendarRate, Vec3 observer, Object settings, Object context) {
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
                    || settingsIdentity != settings || contextIdentity != context) {
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
                contextIdentity = context;
                value = factory.create(level, tick, worldTick, partialTick, rainLevel,
                        calendarRate, observer, settings, context);
                initialized = true;
            }
            return value;
        }

        void clear() {
            initialized = false;
            levelIdentity = null;
            settingsIdentity = null;
            contextIdentity = null;
            value = null;
        }
    }

    @FunctionalInterface
    interface Factory<T> {
        T create(Object level, long calendarTick, long gameTick, float partialTick,
                 float rainLevel, double calendarRate, Vec3 observer, Object settings,
                 Object context);
    }
}
