package first.wildfires.mixin.minecraft;

import first.wildfires.client.ThermalDebugRenderer;
import first.wildfires.thermal.ComplexThermalField;
import first.wildfires.thermal.SimpleThermalField;
import first.wildfires.thermal.ThermalGrid;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps client thermal section caches aligned with block updates from the server. */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "setServerVerifiedBlockState", at = @At("TAIL"))
    private void wildfires$invalidateThermalFieldsFromServer(BlockPos position, BlockState state, int sequence,
                                                             CallbackInfo ci) {
        invalidate(position);
    }

    @Inject(method = "setBlock", at = @At("RETURN"))
    private void wildfires$invalidateThermalFieldsFromPrediction(BlockPos position, BlockState state, int flags,
                                                                  int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            invalidate(position);
        }
    }

    private void invalidate(BlockPos position) {
        ClientLevel level = (ClientLevel) (Object) this;
        ThermalGrid.clear(level);
        SimpleThermalField.invalidateAround(level, position);
        ComplexThermalField.invalidateAround(level, position);
        ThermalDebugRenderer.requestRefresh();
    }
}
