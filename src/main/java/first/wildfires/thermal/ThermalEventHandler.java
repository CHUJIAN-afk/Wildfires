package first.wildfires.thermal;

import first.wildfires.Wildfires;
import first.wildfires.network.ThermalOffsetSyncPacket;
import first.wildfires.register.NetworkPacketRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThermalEventHandler {

    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final float SYNC_THRESHOLD = 0.1F;
    private static final Map<UUID, ThermalWorldManager.ThermalSample> LAST_SENT = new HashMap<>();

    private ThermalEventHandler() {
    }

    @SubscribeEvent
    public static void tickLevel(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            ThermalWorldManager.get(serverLevel).tick();
        }
    }

    @SubscribeEvent
    public static void syncPlayer(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        ThermalWorldManager.ThermalSample sample = ThermalFieldManager.sample(player);
        ThermalWorldManager.ThermalSample previous = LAST_SENT.get(player.getUUID());
        if (previous == null || changed(previous, sample)) {
            LAST_SENT.put(player.getUUID(), sample);
            NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player),
                    new ThermalOffsetSyncPacket(sample.airTemperature(), sample.radiationOffset(),
                            sample.effectiveTemperature()));
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            ThermalWorldManager.get(player.serverLevel()).removePlayer(player);
        }
    }

    @SubscribeEvent
    public static void place(BlockEvent.EntityPlaceEvent event) {
        invalidate(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BlockPos position = event.getPos().immutable();
            level.getServer().execute(() -> invalidate(level, position));
        }
    }

    @SubscribeEvent
    public static void loadChunkData(ChunkDataEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CompoundTag root = event.getData().getCompound(ThermalWorldManager.CHUNK_DATA_KEY);
            ThermalWorldManager.get(level).loadChunkData(event.getChunk().getPos(), root);
        }
    }

    @SubscribeEvent
    public static void saveChunkData(ChunkDataEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getData().put(ThermalWorldManager.CHUNK_DATA_KEY,
                    ThermalWorldManager.get(level).saveChunk(event.getChunk().getPos()));
        }
    }

    @SubscribeEvent
    public static void loadChunk(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            ThermalWorldManager.get(level).queueChunkLoad(chunk.getPos());
        }
    }

    @SubscribeEvent
    public static void unloadChunk(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ThermalWorldManager manager = ThermalWorldManager.get(level);
            var chunkPos = event.getChunk().getPos();
            manager.unloadChunk(chunkPos);
            level.getServer().execute(() -> manager.refreshChunkBorders(chunkPos));
        }
    }

    @SubscribeEvent
    public static void unloadLevel(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ThermalWorldManager.clear(level);
        }
    }

    @SubscribeEvent
    public static void applyNewWorldThermalDefaults(LevelEvent.CreateSpawnPosition event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            ThermalConfig.applyCommonDefaultsToNewWorld();
        }
    }

    public static void invalidate(net.minecraft.world.level.LevelAccessor level, BlockPos position) {
        if (level instanceof Level actualLevel) {
            ThermalFieldManager.invalidateAround(actualLevel, position);
        }
    }

    private static boolean changed(ThermalWorldManager.ThermalSample previous,
                                   ThermalWorldManager.ThermalSample current) {
        return Math.abs(previous.airTemperature() - current.airTemperature()) >= SYNC_THRESHOLD
                || Math.abs(previous.radiationOffset() - current.radiationOffset()) >= SYNC_THRESHOLD
                || Math.abs(previous.effectiveTemperature() - current.effectiveTemperature()) >= SYNC_THRESHOLD;
    }
}
