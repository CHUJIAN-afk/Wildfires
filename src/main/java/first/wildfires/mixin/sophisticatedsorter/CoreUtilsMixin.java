package first.wildfires.mixin.sophisticatedsorter;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.sighs.sophisticatedsorter.utils.CoreUtils", remap = false)
public class CoreUtilsMixin {
    private static final String TFC_RESTRICTED_CHEST_SLOT = "net.dries007.tfc.common.container.RestrictedChestContainer$RestrictedSlot";

    @Inject(method = "isSlotInvalid", at = @At("RETURN"), cancellable = true, remap = false)
    private static void allowTfcRestrictedChestSlots(Slot slot, CallbackInfoReturnable<Boolean> cir) {
        if (slot.getClass().getName().equals(TFC_RESTRICTED_CHEST_SLOT)) {
            cir.setReturnValue(false);
        }
    }
}
