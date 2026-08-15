package first.wildfires.client.space.render;

import first.wildfires.api.celestial.CelestialState;
import first.wildfires.space.celestial.ObservationContext;

import java.util.Objects;

/**
 * Exact single-entry memoization shared by orbit lightmap and sky queries in one rendered frame.
 * Identity and raw floating-point bits are intentional: the cache never approximates time or
 * reuses a frame after any input has changed.
 */
public final class OrbitVisualFrameCache {

    private static final SingleEntryCache<OrbitVisualRules.Frame> CACHE = new SingleEntryCache<>(
            (context, state, gameTime, calendarTicks, calendarRate, daysInMonth) ->
                    OrbitVisualRules.frame((ObservationContext) context, (CelestialState) state,
                            gameTime, calendarTicks, calendarRate, daysInMonth));

    private OrbitVisualFrameCache() {
    }

    public static OrbitVisualRules.Frame frame(ObservationContext context, CelestialState state,
                                                double gameTime, double calendarTicks,
                                                double calendarRate, int calendarDaysInMonth) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(state, "state");
        return CACHE.get(context, state, gameTime, calendarTicks, calendarRate,
                calendarDaysInMonth);
    }

    public static void reset() {
        CACHE.clear();
    }

    static final class SingleEntryCache<T> {
        private final Factory<T> factory;
        private Object contextIdentity;
        private Object stateIdentity;
        private long gameTimeBits;
        private long calendarTicksBits;
        private long calendarRateBits;
        private int calendarDaysInMonth;
        private boolean initialized;
        private T value;

        SingleEntryCache(Factory<T> factory) {
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        synchronized T get(Object context, Object state, double gameTime, double calendarTicks,
                           double calendarRate, int daysInMonth) {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(state, "state");
            long gameBits = Double.doubleToRawLongBits(gameTime);
            long tickBits = Double.doubleToRawLongBits(calendarTicks);
            long rateBits = Double.doubleToRawLongBits(calendarRate);
            if (!initialized || contextIdentity != context || stateIdentity != state
                    || gameTimeBits != gameBits || calendarTicksBits != tickBits
                    || calendarRateBits != rateBits || calendarDaysInMonth != daysInMonth) {
                T created = factory.create(context, state, gameTime, calendarTicks,
                        calendarRate, daysInMonth);
                contextIdentity = context;
                stateIdentity = state;
                gameTimeBits = gameBits;
                calendarTicksBits = tickBits;
                calendarRateBits = rateBits;
                calendarDaysInMonth = daysInMonth;
                value = created;
                initialized = true;
            }
            return value;
        }

        synchronized void clear() {
            initialized = false;
            contextIdentity = null;
            stateIdentity = null;
            value = null;
        }
    }

    @FunctionalInterface
    interface Factory<T> {
        T create(Object context, Object state, double gameTime, double calendarTicks,
                 double calendarRate, int daysInMonth);
    }
}
