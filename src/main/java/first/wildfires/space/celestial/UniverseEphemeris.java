package first.wildfires.space.celestial;

import first.wildfires.api.celestial.CelestialState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Read-only astronomical state source used by future orbit vantage points. */
@FunctionalInterface
public interface UniverseEphemeris {

    CelestialState state(Level level, Vec3 observer, float partialTick);
}
