package first.wildfires.space.celestial;

import first.wildfires.api.celestial.CelestialProvider;
import first.wildfires.api.celestial.CelestialState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/** Celestial API provider for station-local observation in the shared orbit dimension. */
public final class StationCelestialProvider implements CelestialProvider {

    public static final StationCelestialProvider INSTANCE =
            new StationCelestialProvider(ExistingCelestialEphemeris.INSTANCE);

    private final UniverseEphemeris ephemeris;

    StationCelestialProvider(UniverseEphemeris ephemeris) {
        this.ephemeris = Objects.requireNonNull(ephemeris, "ephemeris");
    }

    @Override
    public CelestialState state(Level level, Vec3 observer, float partialTick) {
        return stateOptional(level, observer, partialTick)
                .orElseThrow(() -> new IllegalStateException("No station context at orbit observer"));
    }

    @Override
    public Optional<CelestialState> stateOptional(Level level, Vec3 observer, float partialTick) {
        return ObservationContextResolver.resolve(level, observer).map(context ->
                // A fixed Z=0 reference prevents station grid coordinates from becoming TFE latitude.
                project(ephemeris.state(level, new Vec3(0.0D, observer.y, 0.0D), partialTick), context));
    }

    private static CelestialState project(CelestialState source, ObservationContext context) {
        return new CelestialState(0.0D, source.fractionOfDay(), source.fractionOfYear(),
                source.calendarTicks(), source.sun(), source.moon(), source.celestialNorth(),
                source.orbitingBodies(),
                source.moonPhase(), 0.0D, 0.0D,
                first.wildfires.api.celestial.SolarEclipseState.NONE, 0.0D,
                first.wildfires.api.celestial.LunarEclipseState.NONE, 0.0D, 0.0D,
                source.sunScale(), source.moonScale(), 1.0D, source.daylight());
    }
}
