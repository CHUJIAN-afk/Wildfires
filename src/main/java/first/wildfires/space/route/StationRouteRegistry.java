package first.wildfires.space.route;

import first.wildfires.Wildfires;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DataPackRegistryEvent;

import java.util.Objects;

/** Synchronized datapack registry for directed fixed-duration station routes. */
public final class StationRouteRegistry {

    public static final ResourceKey<Registry<StationRouteDefinition>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(Wildfires.rl("station_routes"));

    private StationRouteRegistry() {
    }

    public static void register(IEventBus bus) {
        Objects.requireNonNull(bus, "bus").addListener(StationRouteRegistry::onNewRegistry);
    }

    public static Registry<StationRouteDefinition> get(RegistryAccess access) {
        return Objects.requireNonNull(access, "access").registryOrThrow(REGISTRY_KEY);
    }

    private static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY_KEY, StationRouteDefinition.CODEC, StationRouteDefinition.CODEC);
    }
}
