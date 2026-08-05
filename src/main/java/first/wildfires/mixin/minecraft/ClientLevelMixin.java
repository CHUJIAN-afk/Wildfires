package first.wildfires.mixin.minecraft;

import first.wildfires.client.ThermalDebugRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Requests a fresh server debug snapshot after client-visible block changes. */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "setServerVerifiedBlockState", at = @At("TAIL"))
    private void wildfires$refreshThermalDebugFromServer(BlockPos position, BlockState state, int sequence,
                                                          CallbackInfo ci) {
        ThermalDebugRenderer.requestRefresh();
    }

    @Inject(method = "setBlock", at = @At("RETURN"))
    private void wildfires$refreshThermalDebugFromPrediction(BlockPos position, BlockState state, int flags,
                                                              int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            ThermalDebugRenderer.requestRefresh();
        }
    }
}
