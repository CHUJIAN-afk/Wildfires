package first.wildfires.celestial;

import first.wildfires.Wildfires;
import first.wildfires.network.CelestialSettingsSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sends server-authoritative celestial settings whenever a client gains a new player/world context. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CelestialSyncEvents {

    private CelestialSyncEvents() {
    }

    /** Creates and dirties the world-specific ephemeris as part of world startup, before any login sync. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        CelestialEphemerisSavedData.get(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    private static void sync(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            new CelestialSettingsSyncPacket(CelestialConfig.serverSettings().withOrbitalPhases(
                    CelestialEphemerisSavedData.get(serverPlayer.server).phases())).sendTo(serverPlayer);
        }
    }
}
