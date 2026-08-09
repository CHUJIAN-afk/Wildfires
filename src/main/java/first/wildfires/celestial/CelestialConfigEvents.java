package first.wildfires.celestial;

import first.wildfires.Wildfires;
import first.wildfires.network.CelestialSettingsSyncPacket;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Re-synchronizes connected players after the celestial server config is reloaded. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CelestialConfigEvents {

    private CelestialConfigEvents() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        if (CelestialConfig.isServerSpec(event.getConfig())) {
            CelestialConfig.refreshServerSettings();
        }
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (!CelestialConfig.isServerSpec(event.getConfig())) {
            return;
        }
        CelestialConfig.refreshServerSettings();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> server.getPlayerList().getPlayers().forEach(player ->
                    new CelestialSettingsSyncPacket(CelestialConfig.serverSettings()).sendTo(player)));
        }
    }
}
