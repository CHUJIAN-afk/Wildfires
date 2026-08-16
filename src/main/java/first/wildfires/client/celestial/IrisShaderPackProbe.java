package first.wildfires.client.celestial;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Optional reflection boundary for the public Iris/Oculus shader-pack activity API. */
final class IrisShaderPackProbe implements BooleanSupplier {

    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final BooleanSupplier INACTIVE = () -> false;

    private final MethodHandle isShaderPackInUse;
    private volatile boolean failed;

    private IrisShaderPackProbe(MethodHandle isShaderPackInUse) {
        this.isShaderPackInUse = isShaderPackInUse;
    }

    static BooleanSupplier discover() {
        return discover(IrisShaderPackProbe.class.getClassLoader());
    }

    static BooleanSupplier discover(ClassLoader loader) {
        try {
            Class<?> apiClass = Class.forName(IRIS_API, false, loader);
            Method getInstance = apiClass.getMethod("getInstance");
            Method inUse = apiClass.getMethod("isShaderPackInUse");
            Object api = getInstance.invoke(null);
            if (api == null) {
                return INACTIVE;
            }
            MethodHandle query = MethodHandles.publicLookup().unreflect(inUse).bindTo(api)
                    .asType(MethodType.methodType(boolean.class));
            return new IrisShaderPackProbe(query);
        } catch (ClassNotFoundException exception) {
            return INACTIVE;
        } catch (ReflectiveOperationException | LinkageError exception) {
            AtomicBoolean firstQuery = new AtomicBoolean(true);
            return () -> {
                if (firstQuery.compareAndSet(true, false)) {
                    throw new IllegalStateException("Iris/Oculus shader API is unavailable", exception);
                }
                return false;
            };
        }
    }

    @Override
    public boolean getAsBoolean() {
        if (failed) {
            return false;
        }
        try {
            return (boolean) isShaderPackInUse.invokeExact();
        } catch (RuntimeException | LinkageError exception) {
            failed = true;
            throw new IllegalStateException("Unable to query the active Iris/Oculus shader pack", exception);
        } catch (Error error) {
            throw error;
        } catch (Throwable exception) {
            failed = true;
            throw new IllegalStateException("Unable to query the active Iris/Oculus shader pack", exception);
        }
    }
}
