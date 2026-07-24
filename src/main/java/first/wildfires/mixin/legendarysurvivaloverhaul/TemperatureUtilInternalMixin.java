package first.wildfires.mixin.legendarysurvivaloverhaul;

import first.wildfires.thermal.ThermalFieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sfiomn.legendarysurvivaloverhaul.util.internal.TemperatureUtilInternal;

/**
 * LSO uses this internal method while updating the player's body temperature.
 */
@Mixin(value = TemperatureUtilInternal.class, remap = false)
public class TemperatureUtilInternalMixin {

    @Inject(method = "getPlayerTargetTemperature", at = @At("RETURN"), cancellable = true)
    private void wildfires$addLocalThermalField(
            Player player,
            CallbackInfoReturnable<Float> cir
    ) {
        cir.setReturnValue(cir.getReturnValue() + ThermalFieldManager.getTemperatureOffset(player));
    }

    @Inject(method = "getWorldTemperature", at = @At("RETURN"), cancellable = true)
    private void wildfires$addLocalWorldTemperature(
            Level level,
            BlockPos position,
            CallbackInfoReturnable<Float> cir
    ) {
        cir.setReturnValue(cir.getReturnValue() + ThermalFieldManager.getTemperatureOffset(level, position));
    }
}
