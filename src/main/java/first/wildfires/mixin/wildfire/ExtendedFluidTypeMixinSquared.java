package first.wildfires.mixin.wildfire;

import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "org.mantodea.wildfire.mixins.tfc.fluid.ExtendedFluidTypeMixin", remap = false, priority = 1500)
public class ExtendedFluidTypeMixinSquared {

    @TargetHandler(
            mixin = "org.mantodea.wildfire.mixins.tfc.fluid.ExtendedFluidTypeMixin",
            name = "supportsBoating"
    )
    @Inject(
            method = "@MixinSquared:Handler",
            at = @At("HEAD"),
            cancellable = true
    )
    private void wildfires$supportsBoating(Boat boat, CallbackInfoReturnable<Boolean> cir) {
        if (boat == null) {
            cir.setReturnValue(true);
        }
    }

}
