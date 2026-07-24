package first.wildfires.thermal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Provides the combined complex and simple thermal fields.
 */
public final class ThermalFieldManager {

    private ThermalFieldManager() {
    }

    public static synchronized float getTemperatureOffset(Player player) {
        if (player.level().isClientSide()) {
            return ClientThermalState.getLocalOffset();
        }
        return getTemperatureOffset(player.level(), player.blockPosition());
    }

    public static synchronized float getTemperatureOffset(Level level, BlockPos position) {
        if (level.isClientSide()) {
            return ClientThermalState.getLocalOffset();
        }
        return ComplexThermalField.get(level, position) + SimpleThermalField.get(level, position);
    }

    public static synchronized void clear(Player player) {
        // Section fields are keyed by world position, not by player identity.
    }

    public static synchronized void invalidateAround(Level level, BlockPos position) {
        // ComplexThermalField and SimpleThermalField own invalidation and caching.
    }

}
