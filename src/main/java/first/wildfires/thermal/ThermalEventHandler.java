package first.wildfires.thermal;

import first.wildfires.Wildfires;
import first.wildfires.network.ThermalOffsetSyncPacket;
import first.wildfires.register.NetworkPacketRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThermalEventHandler {

    private static final int THERMAL_SYNC_INTERVAL_TICKS = 10;
    private static final int THERMAL_CACHE_PRUNE_INTERVAL_TICKS = 200;
    private static final Map<UUID, Float> LAST_SENT_OFFSETS = new HashMap<>();

    private ThermalEventHandler() {
    }

    @SubscribeEvent
    public static void clearCachedField(PlayerEvent.PlayerLoggedOutEvent event) {
        ThermalFieldManager.clear(event.getEntity());
        LAST_SENT_OFFSETS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void syncActivePlayerThermalOffset(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
                || !(event.player instanceof net.minecraft.server.level.ServerPlayer player)
                || player.tickCount % THERMAL_SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        float offset = ThermalFieldManager.getTemperatureOffset(player);
        Float previous = LAST_SENT_OFFSETS.put(player.getUUID(), offset);
        if (previous == null || Math.abs(previous - offset) >= 0.01F) {
            NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player), new ThermalOffsetSyncPacket(offset));
        }
        if (player.tickCount % THERMAL_CACHE_PRUNE_INTERVAL_TICKS == 0) {
            List<BlockPos> activeCenters = new ArrayList<>();
            for (net.minecraft.world.entity.player.Player activePlayer : player.serverLevel().players()) {
                activeCenters.add(activePlayer.blockPosition());
            }
            SimpleThermalField.prune(player.serverLevel(), activeCenters);
            ComplexThermalField.prune(player.serverLevel(), activeCenters);
        }
    }

    @SubscribeEvent
    public static void invalidateSimpleThermalField(BlockEvent.EntityPlaceEvent event) {
        invalidate(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void invalidateSimpleThermalField(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            // BreakEvent fires before the block is replaced with air. Refresh on the server
            // task queue so the simple-source index observes the final block state.
            BlockPos position = event.getPos().immutable();
            level.getServer().execute(() -> invalidate(level, position));
        } else {
            invalidate(event.getLevel(), event.getPos());
        }
    }

    @SubscribeEvent
    public static void invalidateSimpleThermalField(ChunkEvent.Load event) {
        if (event.getLevel() instanceof Level level) {
            SimpleThermalField.invalidateAround(level, event.getChunk().getPos().getWorldPosition());
            ComplexThermalField.invalidateAround(level, event.getChunk().getPos().getWorldPosition());
            ThermalGrid.clear(level);
        }
    }

    @SubscribeEvent
    public static void clearThermalFieldsOnLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            SimpleThermalField.clear(level);
            ComplexThermalField.clear(level);
            ThermalGrid.clear(level);
            if (level.isClientSide()) {
                ClientThermalState.setLocalOffset(0.0F);
            }
        }
    }

    public static void invalidate(net.minecraft.world.level.LevelAccessor level, BlockPos position) {
        if (level instanceof Level actualLevel) {
            ThermalGrid.clear(actualLevel);
            SimpleThermalField.invalidateAround(actualLevel, position);
            ComplexThermalField.invalidateAround(actualLevel, position);
            ThermalFieldManager.invalidateAround(actualLevel, position);
        }
    }
}
