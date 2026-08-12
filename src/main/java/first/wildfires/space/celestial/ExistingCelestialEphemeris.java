package first.wildfires.space.celestial;

import first.wildfires.api.celestial.CelestialProvider;
import first.wildfires.api.celestial.CelestialState;
import first.wildfires.celestial.OverworldCelestialProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** Exact adapter over the existing overworld provider; it never recalculates celestial mathematics. */
public final class ExistingCelestialEphemeris implements UniverseEphemeris {

    public static final ExistingCelestialEphemeris INSTANCE =
            new ExistingCelestialEphemeris(OverworldCelestialProvider.INSTANCE);

    private final CelestialProvider delegate;

    ExistingCelestialEphemeris(CelestialProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public CelestialState state(Level level, Vec3 observer, float partialTick) {
        return delegate.state(level, observer, partialTick);
    }
}
