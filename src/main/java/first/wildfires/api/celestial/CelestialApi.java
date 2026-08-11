package first.wildfires.api.celestial;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Dimension-scoped celestial query API. Registration does not alter vanilla time or lighting. */
public final class CelestialApi {

    private static final Map<ResourceKey<Level>, CelestialProvider> PROVIDERS = new ConcurrentHashMap<>();

    private CelestialApi() {
    }

    public static void register(ResourceKey<Level> dimension, CelestialProvider provider) {
        CelestialProvider previous = PROVIDERS.putIfAbsent(dimension, provider);
        if (previous != null && previous != provider) {
            throw new IllegalStateException("A celestial provider is already registered for " + dimension.location());
        }
    }

    public static Optional<CelestialState> state(Level level, Vec3 observer, float partialTick) {
        CelestialProvider provider = PROVIDERS.get(level.dimension());
        return provider == null ? Optional.empty() : Optional.of(provider.state(level, observer, partialTick));
    }

    public static Optional<DaylightState> daylight(Level level, BlockPos observer) {
        return state(level, observer.getCenter(), 0.0F).map(CelestialState::daylight);
    }

    /** Current local Sun/Moon event state with horizon and day/night eligibility already applied. */
    public static Optional<CelestialEventState> events(Level level, BlockPos observer) {
        return state(level, observer.getCenter(), 0.0F).map(CelestialEventState::from);
    }
}
