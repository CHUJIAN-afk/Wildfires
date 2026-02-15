package first.wildfires.mixin.wildfire;

import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.world.entity.vehicle.Boat;
import org.mantodea.wildfire.mixins.tfc.fluid.ExtendedFluidTypeMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ExtendedFluidTypeMixin.class, remap = false)
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
