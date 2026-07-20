package first.wildfires.mixin.minecraft;

import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemColors.class)
public class ItemColorsMixin {

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private void wildfires$ignoreEmptyItemStacks(ItemStack stack, int tintIndex, CallbackInfoReturnable<Integer> cir) {
        // Some third-party item color handlers assume every stack contains a BlockItem.
        if (stack.isEmpty()) {
            cir.setReturnValue(-1);
        }
    }
}
