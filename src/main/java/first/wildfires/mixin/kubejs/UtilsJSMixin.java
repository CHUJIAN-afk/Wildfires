package first.wildfires.mixin.kubejs;

import dev.latvian.mods.kubejs.util.UtilsJS;
import first.wildfires.compats.kubejs.event.TFCFluidEvents;
import first.wildfires.compats.kubejs.event.TFCFluidModificationEventJS;
import first.wildfires.compats.kubejs.event.ThermalSourceEventJS;
import first.wildfires.compats.kubejs.event.ThermalSourceEvents;
import first.wildfires.compats.legendarysurvivaloverhaul.TemperatureRangeManager;
import first.wildfires.thermal.ThermalBoundaryRegistry;
import first.wildfires.thermal.ThermalSourceRegistry;
import net.dries007.tfc.common.fluids.ExtendedFluidType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UtilsJS.class, remap = false)
public class UtilsJSMixin {

    @Inject(method = "postModificationEvents", at = @At("TAIL"), remap = false)
    private static void postModificationEvents(CallbackInfo ci) {
        ThermalSourceRegistry.resetToBuiltins();
        ThermalBoundaryRegistry.clearOverrides();
        ThermalSourceEvents.SOURCES.post(new ThermalSourceEventJS());
        if (ModList.get().isLoaded("legendarysurvivaloverhaul")) {
            TemperatureRangeManager.postKubeJsModificationEvents();
        }

        for (var fluid : ForgeRegistries.FLUIDS.getValues()) {
            if (fluid.getFluidType() instanceof ExtendedFluidType extendedFluidType) {
                TFCFluidEvents.MODIFY.post(new TFCFluidModificationEventJS(extendedFluidType, extendedFluidType.toString()));
            }
        }
    }

}
