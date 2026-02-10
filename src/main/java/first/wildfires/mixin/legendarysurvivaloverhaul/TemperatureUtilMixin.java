package first.wildfires.mixin.legendarysurvivaloverhaul;

import first.wildfires.api.customEvent.PlayerTargetTemperatureModifyEvent;
import first.wildfires.utils.WildfiresUtil;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;

@Mixin(value = TemperatureUtil.class,remap = false)
public class TemperatureUtilMixin {

    @Inject(
            method = "getPlayerTargetTemperature",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void getPlayerTargetTemperature(Player player, CallbackInfoReturnable<Float> cir) {
        PlayerTargetTemperatureModifyEvent event = new PlayerTargetTemperatureModifyEvent(player, cir.getReturnValue());
        WildfiresUtil.post(event);
        float oldTemperature = event.getOldTemperature();
        float newTemperature = event.getNewTemperature();
        if (oldTemperature != newTemperature) {
            cir.setReturnValue(newTemperature);
        }
    }

}
