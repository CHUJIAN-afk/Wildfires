package first.wildfires.api.celestial;

import java.util.Optional;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface CelestialProvider {
    CelestialState state(Level level, Vec3 observer, float partialTick);

    /** Allows position-scoped providers to report that no observation context exists. */
    default Optional<CelestialState> stateOptional(Level level, Vec3 observer, float partialTick) {
        return Optional.of(state(level, observer, partialTick));
    }
}
