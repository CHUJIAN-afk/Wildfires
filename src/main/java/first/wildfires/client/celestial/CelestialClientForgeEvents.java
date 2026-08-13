package first.wildfires.client.celestial;

import com.mojang.blaze3d.systems.RenderSystem;
import first.wildfires.Wildfires;
import first.wildfires.celestial.CelestialSettingsCache;
import first.wildfires.client.space.SpaceClientState;
import first.wildfires.client.space.OrbitVisualDebugClock;
import first.wildfires.client.space.render.OrbitSkyRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Clears synchronized settings and GPU-owned sky buffers when the client leaves a world. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CelestialClientForgeEvents {

    private CelestialClientForgeEvents() {}

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        SpaceClientState.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        OrbitVisualDebugClock.clear();
        CelestialSettingsCache.reset();
        SpaceClientState.clear();
        if (RenderSystem.isOnRenderThread()) {
            CelestialRenderer.close();
            OrbitSkyRenderer.close();
        } else {
            RenderSystem.recordRenderCall(() -> {
                CelestialRenderer.close();
                OrbitSkyRenderer.close();
            });
        }
    }

}
