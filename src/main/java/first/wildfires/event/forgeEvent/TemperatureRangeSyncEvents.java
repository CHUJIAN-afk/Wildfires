package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import first.wildfires.network.TemperatureRangesSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Installs the dedicated server's KubeJS-defined LSO bands on each joining client. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TemperatureRangeSyncEvents {

    private TemperatureRangeSyncEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (Wildfires.LSOLoaded && event.getEntity() instanceof ServerPlayer player) {
            TemperatureRangesSyncPacket.send(player);
        }
    }
}
