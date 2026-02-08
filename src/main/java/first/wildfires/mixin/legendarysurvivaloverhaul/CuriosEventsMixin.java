package first.wildfires.mixin.legendarysurvivaloverhaul;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sfiomn.legendarysurvivaloverhaul.LegendarySurvivalOverhaul;
import sfiomn.legendarysurvivaloverhaul.common.integration.curios.CuriosEvents;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

@Mixin(value = CuriosEvents.class, remap = false)
public class CuriosEventsMixin {

    @Inject(
            method = "onCurioAttributeModifierEvent",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true
    )
    private static void isLoaded(CurioAttributeModifierEvent event, CallbackInfo ci) {
        LegendarySurvivalOverhaul.curiosLoaded = false;
        ci.cancel();
    }

}
