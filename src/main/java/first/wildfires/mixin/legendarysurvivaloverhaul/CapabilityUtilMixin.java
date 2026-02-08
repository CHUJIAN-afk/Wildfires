package first.wildfires.mixin.legendarysurvivaloverhaul;

import first.wildfires.api.customEvent.ItemTemperatureModifyEvent;
import first.wildfires.utils.WildfiresUtil;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.temperature.TemperatureItemCapability;
import sfiomn.legendarysurvivaloverhaul.util.CapabilityUtil;

@Mixin(value = CapabilityUtil.class, remap = false)
public class CapabilityUtilMixin {

    @Inject(
            method = "getTempItemCapability",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void getTempItemCapability(ItemStack itemStack, CallbackInfoReturnable<TemperatureItemCapability> cir) {
        TemperatureItemCapability capability = cir.getReturnValue();
        if (capability != null) {
            ItemTemperatureModifyEvent event = new ItemTemperatureModifyEvent(itemStack, capability.getWorldTemperatureLevel());
            WildfiresUtil.post(event);
            float oldTemperature = event.getOldTemperature();
            float newTemperature = event.getNewTemperature();
            if (oldTemperature != newTemperature) {
                capability.setWorldTemperatureLevel(newTemperature);
            }
            //capability.setWorldTemperatureLevel(capability.getWorldTemperatureLevel() + HeatCapability.getTemperature(itemStack) * 0.1F);
        }
        cir.setReturnValue(capability);
    }

}
