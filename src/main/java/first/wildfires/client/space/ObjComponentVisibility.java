package first.wildfires.client.space;

import java.util.Objects;
import java.util.Set;

/** Per-render-call OBJ component filter consumed by the Forge composite-renderable mixin. */
public final class ObjComponentVisibility {

    private static final ThreadLocal<Set<String>> ACTIVE = new ThreadLocal<>();

    private ObjComponentVisibility() {
    }

    public static boolean visible(String component) {
        Set<String> components = ACTIVE.get();
        return components == null || components.contains(component);
    }

    /** Enters an explicit component scope without allocating a predicate, lambda, or scope object. */
    public static Set<String> enter(Set<String> components) {
        Objects.requireNonNull(components, "components");
        Set<String> previous = ACTIVE.get();
        ACTIVE.set(components);
        return previous;
    }

    public static void exit(Set<String> previous) {
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
    }

    /** Only an explicitly selected NTM mesh is eligible for the exact BufferBuilder fast path. */
    public static boolean fastPathActive() {
        return ACTIVE.get() != null;
    }
}
