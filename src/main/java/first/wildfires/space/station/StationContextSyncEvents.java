package first.wildfires.space.station;

import first.wildfires.Wildfires;
import first.wildfires.network.StationContextPacket;
import first.wildfires.network.StationRemovedPacket;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationContextResolver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Sends one bounded station snapshot only when a player's resolved orbit context changes. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StationContextSyncEvents {

    private static final Map<UUID, ObservationContext> LAST_SENT = new HashMap<>();

    private StationContextSyncEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity(), true);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity(), true);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity(), true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            sync(event.player, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SENT.clear();
    }

    private static void sync(net.minecraft.world.entity.player.Player entity, boolean force) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        UUID playerId = player.getUUID();
        ObservationContext previous = LAST_SENT.get(playerId);
        Optional<ObservationContext> resolved = ObservationContextResolver.resolve(
                player.level(), player.position());
        if (resolved.isPresent()) {
            ObservationContext current = resolved.orElseThrow();
            if (force || !current.equals(previous)) {
                LAST_SENT.put(playerId, current);
                new StationContextPacket(current).sendTo(player);
            }
        } else if (previous != null) {
            LAST_SENT.remove(playerId);
            new StationRemovedPacket(previous.stationId(), previous.stationRevision()).sendTo(player);
        }
    }
}
