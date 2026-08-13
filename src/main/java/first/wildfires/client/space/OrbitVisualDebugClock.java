package first.wildfires.client.space;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.OptionalDouble;

/** Development-only deterministic time override shared by orbit sky and lightmap acceptance checks. */
public final class OrbitVisualDebugClock {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static double gameTime = Double.NaN;
    private static double calendarTicks = Double.NaN;

    private OrbitVisualDebugClock() {
    }

    public static synchronized OptionalDouble gameTime() {
        return available(gameTime);
    }

    public static synchronized OptionalDouble calendarTicks() {
        return available(calendarTicks);
    }

    public static synchronized void set(double newGameTime, double newCalendarTicks) {
        if (FMLEnvironment.production) {
            throw new IllegalStateException("Orbit visual debug time is unavailable in production");
        }
        if (!Double.isFinite(newGameTime) || newGameTime < 0.0D
                || !Double.isFinite(newCalendarTicks) || newCalendarTicks < 0.0D) {
            throw new IllegalArgumentException("Orbit visual debug time must be finite and non-negative");
        }
        gameTime = newGameTime;
        calendarTicks = newCalendarTicks;
        LOGGER.info("Wildfires orbit visual time frozen: game={}, calendar={}",
                newGameTime, newCalendarTicks);
    }

    public static synchronized void clear() {
        gameTime = Double.NaN;
        calendarTicks = Double.NaN;
        if (!FMLEnvironment.production) {
            LOGGER.info("Wildfires orbit visual time returned to synchronized clocks");
        }
    }

    private static OptionalDouble available(double value) {
        return !FMLEnvironment.production && Double.isFinite(value)
                ? OptionalDouble.of(value) : OptionalDouble.empty();
    }
}
