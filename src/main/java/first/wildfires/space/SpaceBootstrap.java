package first.wildfires.space;

import first.wildfires.space.celestial.CelestialDefinitionRegistry;
import first.wildfires.space.celestial.CelestialRegistryRuntime;
import first.wildfires.space.content.SpaceContentRegister;
import first.wildfires.space.station.StationServerEvents;
import first.wildfires.space.route.StationRouteRuntime;
import net.minecraftforge.eventbus.api.IEventBus;

/** Common registration entry point for the Wildfires space system. */
public final class SpaceBootstrap {

    private SpaceBootstrap() {
    }

    public static void register(IEventBus modEventBus) {
        SpaceContentRegister.register(modEventBus);
        CelestialDefinitionRegistry.register(modEventBus);
        CelestialRegistryRuntime.register();
        StationRouteRuntime.register(modEventBus);
        StationServerEvents.register();
    }
}
