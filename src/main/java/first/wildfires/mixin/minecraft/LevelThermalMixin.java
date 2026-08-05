package first.wildfires.mixin.minecraft;

import first.wildfires.thermal.ThermalEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Invalidates local thermal geometry after any successful server block-state change. */
@Mixin(Level.class)
public abstract class LevelThermalMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void wildfires$refreshThermalGeometry(BlockPos position, BlockState state, int flags,
                                                   int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (cir.getReturnValue() && !level.isClientSide()) {
            ThermalEventHandler.invalidate(level, position);
        }
    }
}
