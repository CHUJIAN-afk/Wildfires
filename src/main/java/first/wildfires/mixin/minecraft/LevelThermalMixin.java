package first.wildfires.mixin.minecraft;

import first.wildfires.thermal.ThermalEventHandler;
import first.wildfires.thermal.ThermalSourceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Refreshes cached thermal fields when a thermal source is added, removed, or changes state. */
@Mixin(Level.class)
public abstract class LevelThermalMixin {

    @Inject(method = "setBlock", at = @At("HEAD"))
    private void wildfires$invalidatePreviousThermalSource(BlockPos position, BlockState state, int flags,
                                                            CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (!level.isClientSide() && ThermalSourceRegistry.isThermalSource(level.getBlockState(position))) {
            ThermalEventHandler.invalidate(level, position);
        }
    }

    @Inject(method = "setBlock", at = @At("RETURN"))
    private void wildfires$invalidateNewThermalSource(BlockPos position, BlockState state, int flags,
                                                       CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (cir.getReturnValue() && !level.isClientSide() && ThermalSourceRegistry.isThermalSource(state)) {
            ThermalEventHandler.invalidate(level, position);
        }
    }
}
