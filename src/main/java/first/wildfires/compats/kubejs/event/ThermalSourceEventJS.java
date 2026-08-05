package first.wildfires.compats.kubejs.event;

import dev.latvian.mods.kubejs.event.EventJS;
import first.wildfires.thermal.ThermalBoundaryRegistry;
import first.wildfires.thermal.ThermalSourceRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Map;

/** Startup-only KubeJS API for exact-block, block-tag, and fluid thermal sources plus passive wall loss. */
public class ThermalSourceEventJS extends EventJS {

    public void source(String blockId, float surfaceTemperature, float faceHeatingRate,
                       @Nullable Float radiationTemperature, @Nullable Float radiationDecayPerBlock) {
        if (blockId.startsWith("#")) {
            sourceTag(blockId, surfaceTemperature, faceHeatingRate,
                    radiationTemperature, radiationDecayPerBlock);
            return;
        }
        ThermalSourceRegistry.register(resolveBlockOrFluid(blockId), new ThermalSourceRegistry.ThermalSourceDefinition(
                surfaceTemperature, faceHeatingRate, radiationTemperature, radiationDecayPerBlock));
    }

    public void state(String blockId, Map<String, ?> properties, float surfaceTemperature, float faceHeatingRate,
                      @Nullable Float radiationTemperature, @Nullable Float radiationDecayPerBlock) {
        if (blockId.startsWith("#")) {
            throw new IllegalArgumentException("State thermal sources require an exact block or fluid ID; "
                    + "use sourceTag for a block tag without state properties: " + blockId);
        }
        ThermalSourceRegistry.registerState(resolveBlockOrFluid(blockId), properties,
                new ThermalSourceRegistry.ThermalSourceDefinition(
                        surfaceTemperature, faceHeatingRate, radiationTemperature, radiationDecayPerBlock));
    }

    public void sourceTag(String tagId, float surfaceTemperature, float faceHeatingRate,
                          @Nullable Float radiationTemperature, @Nullable Float radiationDecayPerBlock) {
        ThermalSourceRegistry.registerTag(resolveBlockTag(tagId),
                new ThermalSourceRegistry.ThermalSourceDefinition(
                        surfaceTemperature, faceHeatingRate, radiationTemperature, radiationDecayPerBlock));
    }

    public void fluid(String fluidId, float surfaceTemperature, float faceHeatingRate,
                      @Nullable Float radiationTemperature, @Nullable Float radiationDecayPerBlock) {
        ThermalSourceRegistry.register(resolveFluidBlock(fluidId),
                new ThermalSourceRegistry.ThermalSourceDefinition(
                        surfaceTemperature, faceHeatingRate, radiationTemperature, radiationDecayPerBlock));
    }

    public void fluidState(String fluidId, Map<String, ?> properties, float surfaceTemperature,
                           float faceHeatingRate, @Nullable Float radiationTemperature,
                           @Nullable Float radiationDecayPerBlock) {
        ThermalSourceRegistry.registerState(resolveFluidBlock(fluidId), properties,
                new ThermalSourceRegistry.ThermalSourceDefinition(
                        surfaceTemperature, faceHeatingRate, radiationTemperature, radiationDecayPerBlock));
    }

    public void remove(String blockId) {
        if (blockId.startsWith("#")) {
            removeSourceTag(blockId);
            return;
        }
        ThermalSourceRegistry.unregister(resolveBlockOrFluid(blockId));
    }

    public void removeSourceTag(String tagId) {
        ThermalSourceRegistry.unregisterTag(resolveBlockTag(tagId));
    }

    public void removeFluid(String fluidId) {
        ThermalSourceRegistry.unregister(resolveFluidBlock(fluidId));
    }

    public void blockLoss(String blockId, float loss) {
        ThermalBoundaryRegistry.registerBlock(resolveBlock(blockId), loss);
    }

    public void tagLoss(String tagId, float loss) {
        String normalized = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        ResourceLocation id = ResourceLocation.tryParse(normalized);
        if (id == null) {
            throw new IllegalArgumentException("Invalid block tag: " + tagId);
        }
        ThermalBoundaryRegistry.registerTag(id, loss);
    }

    private static Block resolveBlock(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("Unknown thermal source block: " + blockId);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static ResourceLocation resolveBlockTag(String tagId) {
        String normalized = tagId.startsWith("#") ? tagId.substring(1) : tagId;
        ResourceLocation id = ResourceLocation.tryParse(normalized);
        if (id == null) {
            throw new IllegalArgumentException("Invalid thermal source block tag: " + tagId);
        }
        return id;
    }

    private static Block resolveBlockOrFluid(String idText) {
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id);
        }
        return resolveFluidBlock(idText);
    }

    private static Block resolveFluidBlock(String fluidId) {
        ResourceLocation id = ResourceLocation.tryParse(fluidId);
        Fluid fluid = id == null ? null : ForgeRegistries.FLUIDS.getValue(id);
        if (fluid == null) {
            throw new IllegalArgumentException("Unknown thermal source fluid: " + fluidId);
        }
        BlockState legacyState = fluid.defaultFluidState().createLegacyBlock();
        Block block = legacyState.getBlock();
        if (block == Blocks.AIR && BuiltInRegistries.BLOCK.containsKey(id)) {
            Block sameIdBlock = BuiltInRegistries.BLOCK.get(id);
            if (sameIdBlock != Blocks.AIR) {
                block = sameIdBlock;
            }
        }
        if (block == Blocks.AIR) {
            throw new IllegalArgumentException("Thermal source fluid has no placeable world block: " + fluidId);
        }
        return block;
    }
}
