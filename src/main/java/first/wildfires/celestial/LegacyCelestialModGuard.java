package first.wildfires.celestial;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Rejects legacy sky mods whose rendering and resources have been absorbed by Wildfires. */
public final class LegacyCelestialModGuard {

    private static final List<String> LEGACY_MOD_IDS = List.of("caelum", "tfccaelum");

    private LegacyCelestialModGuard() {
    }

    public static void rejectLoaded(Predicate<String> isLoaded) {
        Objects.requireNonNull(isLoaded, "isLoaded");
        List<String> detected = LEGACY_MOD_IDS.stream().filter(isLoaded).toList();
        if (!detected.isEmpty()) {
            throw new IllegalStateException("Wildfires contains the Caelum/TFCCaelum sky system; "
                    + "remove the legacy Caelum and TFCCaelum jars (detected: "
                    + String.join(", ", detected) + ")");
        }
    }
}
