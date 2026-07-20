package first.wildfires.mixin.sophisticatedsorter;

import net.dries007.tfc.common.container.RestrictedChestContainer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.sighs.sophisticatedsorter.utils.ClientUtils", remap = false)
public class ClientUtilsMixin {
    @Inject(method = "isValidScreen", at = @At("RETURN"), cancellable = true, remap = false)
    private static void allowTfcRestrictedChests(CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().player != null
            && Minecraft.getInstance().player.containerMenu instanceof RestrictedChestContainer) {
            cir.setReturnValue(true);
        }
    }
}
