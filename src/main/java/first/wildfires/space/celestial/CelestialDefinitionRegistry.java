package first.wildfires.space.celestial;

import first.wildfires.Wildfires;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DataPackRegistryEvent;

import java.util.Objects;

/** Forge data-pack registry entry point for synchronized Wildfires celestial definitions. */
public final class CelestialDefinitionRegistry {

    public static final ResourceKey<Registry<CelestialDefinition>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(Wildfires.rl("celestials"));

    private CelestialDefinitionRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "modEventBus")
                .addListener(CelestialDefinitionRegistry::onNewRegistry);
    }

    public static Registry<CelestialDefinition> get(RegistryAccess registryAccess) {
        return Objects.requireNonNull(registryAccess, "registryAccess").registryOrThrow(REGISTRY_KEY);
    }

    private static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY_KEY, CelestialDefinition.CODEC, CelestialDefinition.CODEC);
    }
}
