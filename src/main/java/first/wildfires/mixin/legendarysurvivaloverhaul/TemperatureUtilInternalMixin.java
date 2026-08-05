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

    /** Prevents getWorldTemperature calls nested inside a player query from adding the same field twice. */
    private static final ThreadLocal<Integer> WILDFIRES_PLAYER_QUERY_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    @Inject(method = "getPlayerTargetTemperature", at = @At("HEAD"))
    private void wildfires$beginPlayerThermalQuery(
            Player player,
            CallbackInfoReturnable<Float> cir
    ) {
        WILDFIRES_PLAYER_QUERY_DEPTH.set(WILDFIRES_PLAYER_QUERY_DEPTH.get() + 1);
    }

    @Inject(method = "getPlayerTargetTemperature", at = @At("RETURN"), cancellable = true)
    private void wildfires$addLocalThermalField(
            Player player,
            CallbackInfoReturnable<Float> cir
    ) {
        int depth = WILDFIRES_PLAYER_QUERY_DEPTH.get();
        try {
            cir.setReturnValue(cir.getReturnValue()
                    + ThermalFieldManager.getAppliedPlayerTemperatureOffset(player));
        } finally {
            if (depth <= 1) {
                WILDFIRES_PLAYER_QUERY_DEPTH.remove();
            } else {
                WILDFIRES_PLAYER_QUERY_DEPTH.set(depth - 1);
            }
        }
    }

    @Inject(method = "getWorldTemperature", at = @At("RETURN"), cancellable = true)
    private void wildfires$addLocalWorldTemperature(
            Level level,
            BlockPos position,
            CallbackInfoReturnable<Float> cir
    ) {
        if (WILDFIRES_PLAYER_QUERY_DEPTH.get() == 0) {
            cir.setReturnValue(cir.getReturnValue() + ThermalFieldManager.getTemperatureOffset(level, position));
        }
    }
}
