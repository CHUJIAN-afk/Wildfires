package first.wildfires.thermal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Stable facade used by LSO, networking, and diagnostics. */
public final class ThermalFieldManager {

    private ThermalFieldManager() {
    }

    public static float getTemperatureOffset(Player player) {
        if (player.level().isClientSide()) {
            return ClientThermalState.getEffectiveOffset();
        }
        if (player instanceof ServerPlayer serverPlayer) {
            return sample(serverPlayer).effectiveTemperature();
        }
        return getTemperatureOffset(player.level(), player.blockPosition());
    }

    /** The gameplay-facing player bonus is intentionally coarser than the full-precision field. */
    public static float getAppliedPlayerTemperatureOffset(Player player) {
        return Math.round(getTemperatureOffset(player) * 10.0F) / 10.0F;
    }

    public static float getTemperatureOffset(Level level, BlockPos position) {
        if (level.isClientSide()) {
            return ClientThermalState.getEffectiveOffset();
        }
        return level instanceof ServerLevel serverLevel
                ? ThermalWorldManager.get(serverLevel).sample(position).effectiveTemperature()
                : 0.0F;
    }

    public static ThermalWorldManager.ThermalSample sample(ServerPlayer player) {
        return ThermalWorldManager.get(player.serverLevel()).sample(player);
    }

    public static float getClientAirTemperature() {
        return ClientThermalState.getAirTemperature();
    }

    public static float getClientRadiationOffset() {
        return ClientThermalState.getRadiationOffset();
    }

    public static void invalidateAround(Level level, BlockPos position) {
        if (level instanceof ServerLevel serverLevel) {
            ThermalWorldManager.get(serverLevel).onBlockChanged(position.immutable());
        }
    }
}
