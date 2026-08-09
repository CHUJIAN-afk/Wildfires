package first.wildfires.celestial;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/** Isolates TFE's currently client-named public hemisphere helper from common celestial code. */
final class TfeHemisphereScale {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float FALLBACK = 20000.0F;
    private static MethodHandle method;
    private static boolean resolved;

    private TfeHemisphereScale() {
    }

    static float get(Level level) {
        if (!resolved) {
            resolve();
        }
        if (method != null) {
            try {
                return validOrFallback(invoke(method, level));
            } catch (Throwable exception) {
                if (exception instanceof Error error) {
                    throw error;
                }
                LOGGER.warn("Unable to read TerraFirmaEarth hemisphere scale; using {}", FALLBACK, exception);
                method = null;
            }
        }
        return FALLBACK;
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> helper = Class.forName("com.newterraearth.tfe.client.NTEClimateRenderHelpers");
            method = adapt(helper.getMethod("getHemisphereScale", Level.class));
        } catch (ReflectiveOperationException exception) {
            LOGGER.warn("TerraFirmaEarth hemisphere helper is unavailable; using {}", FALLBACK, exception);
        }
    }

    static MethodHandle adapt(Method candidate) throws IllegalAccessException, NoSuchMethodException {
        if (!Modifier.isStatic(candidate.getModifiers()) || candidate.getReturnType() != float.class
                || candidate.getParameterCount() != 1 || candidate.getParameterTypes()[0] != Level.class) {
            throw new NoSuchMethodException("Expected public static float getHemisphereScale(Level)");
        }
        return MethodHandles.publicLookup().unreflect(candidate)
                .asType(MethodType.methodType(float.class, Level.class));
    }

    static float invoke(MethodHandle handle, Level level) throws Throwable {
        return (float) handle.invokeExact(level);
    }

    static float validOrFallback(float value) {
        return Float.isFinite(value) && value != 0.0F ? value : FALLBACK;
    }
}
