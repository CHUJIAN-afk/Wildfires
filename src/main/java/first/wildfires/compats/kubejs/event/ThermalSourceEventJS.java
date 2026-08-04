package first.wildfires.compats.kubejs.event;

import dev.latvian.mods.kubejs.event.EventJS;
import first.wildfires.thermal.ThermalSourceRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Map;

/** Startup-only KubeJS API for registering virtual thermal sources. */
public class ThermalSourceEventJS extends EventJS {

    public void complex(String blockId, float maximumTemperature, float radiationTemperature,
                        int radiationRadius, Float propagationLossPerBlock) {
        register(blockId, maximumTemperature, radiationTemperature, radiationRadius,
                false, false, propagationLossPerBlock, false);
    }

    public void simple(String blockId, float maximumTemperature, float radiationTemperature,
                       int radiationRadius, Float propagationLossPerBlock) {
        register(blockId, maximumTemperature, radiationTemperature, radiationRadius,
                false, false, propagationLossPerBlock, true);
    }

    /**
     * Registers a simple source that only applies when the block state contains every given property value.
     * Example: state("tfc:charcoal_forge", Map.of("heat_level", 7), 21, 21, 8, 0.8F).
     */
    public void state(String blockId, Map<String, ?> properties, float maximumTemperature, float radiationTemperature,
                      int radiationRadius, Float propagationLossPerBlock) {
        Block block = resolveBlock(blockId);
        ThermalSourceRegistry.registerState(block, properties, new ThermalSourceRegistry.ThermalSourceDefinition(
                maximumTemperature,
                radiationTemperature,
                radiationRadius,
                false,
                false,
                propagationLossPerBlock,
                true
        ));
    }

    /**
     * Registers or replaces a thermal source definition.
     * Use {@code null} for propagationLossPerBlock to use radiationTemperature / radiationRadius.
     */
    public void register(String blockId, float maximumTemperature, float radiationTemperature, int radiationRadius,
                         boolean readBlockEntityTemperature, boolean forceLoad, Float propagationLossPerBlock,
                         boolean simpleHeatSource) {
        Block block = resolveBlock(blockId);
        ThermalSourceRegistry.register(block, new ThermalSourceRegistry.ThermalSourceDefinition(
                maximumTemperature,
                radiationTemperature,
                radiationRadius,
                readBlockEntityTemperature,
                forceLoad,
                propagationLossPerBlock,
                simpleHeatSource
        ));
    }

    /** Removes a built-in or previously registered thermal source definition. */
    public void remove(String blockId) {
        ThermalSourceRegistry.unregister(resolveBlock(blockId));
    }

    private static Block resolveBlock(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("Unknown thermal source block: " + blockId);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }
}
