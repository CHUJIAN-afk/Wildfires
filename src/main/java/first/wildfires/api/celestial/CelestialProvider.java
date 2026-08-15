package first.wildfires.api.celestial;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface CelestialProvider {
    CelestialState state(Level level, Vec3 observer, float partialTick);

    /** Allows position-scoped providers to report that no observation context exists. */
    default Optional<CelestialState> stateOptional(Level level, Vec3 observer, float partialTick) {
        return Optional.of(state(level, observer, partialTick));
    }

    /** Fast-query hook; custom providers retain the exact full-state derivation by default. */
    default Optional<DaylightState> daylightOptional(Level level, Vec3 observer,
                                                     float partialTick) {
        return stateOptional(level, observer, partialTick).map(CelestialState::daylight);
    }

    /**
     * Block-position fast-query hook. The default deliberately preserves the original centered
     * Vec3 dispatch so existing third-party providers keep exactly the same observation semantics.
     */
    default Optional<DaylightState> daylightOptional(Level level, BlockPos observer,
                                                     float partialTick) {
        return daylightOptional(level, observer.getCenter(), partialTick);
    }

    /** Fast-query hook; custom providers retain the exact full-state derivation by default. */
    default Optional<CelestialEventState> eventsOptional(Level level, Vec3 observer,
                                                         float partialTick) {
        return stateOptional(level, observer, partialTick).map(CelestialEventState::from);
    }

    /**
     * Block-position fast-query hook. The default deliberately preserves the original centered
     * Vec3 dispatch so existing third-party providers keep exactly the same observation semantics.
     */
    default Optional<CelestialEventState> eventsOptional(Level level, BlockPos observer,
                                                         float partialTick) {
        return eventsOptional(level, observer.getCenter(), partialTick);
    }
}
