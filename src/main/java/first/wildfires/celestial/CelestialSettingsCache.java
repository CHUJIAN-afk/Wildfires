package first.wildfires.celestial;

import java.util.Objects;

/** Client-side copy of the server-authoritative celestial settings without client-only class references. */
public final class CelestialSettingsCache {

    private static volatile CelestialRuntimeSettings current = CelestialRuntimeSettings.DEFAULT;

    private CelestialSettingsCache() {
    }

    public static CelestialRuntimeSettings current() {
        return current;
    }

    public static void accept(CelestialRuntimeSettings settings) {
        current = Objects.requireNonNull(settings, "settings");
    }

    public static void reset() {
        current = CelestialRuntimeSettings.DEFAULT;
    }
}
