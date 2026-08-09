package first.wildfires.api.celestial;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface CelestialProvider {
    CelestialState state(Level level, Vec3 observer, float partialTick);
}
