package first.wildfires.diagnostics;

import first.wildfires.Wildfires;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT)
public final class ClientStartupDiagnostics {

    private static long worldConnectionStartedAt;
    private static boolean loggedFirstClientTick;

    private ClientStartupDiagnostics() {
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        worldConnectionStartedAt = StartupDiagnostics.now();
        loggedFirstClientTick = false;
        StartupDiagnostics.clientMark("joined world " + event.getPlayer().level().dimension().location());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!loggedFirstClientTick && event.phase == TickEvent.Phase.END && worldConnectionStartedAt != 0L) {
            loggedFirstClientTick = true;
            StartupDiagnostics.clientCompleted("first tick after world join", worldConnectionStartedAt);
        }
    }
}
