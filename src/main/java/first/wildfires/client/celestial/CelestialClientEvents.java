package first.wildfires.client.celestial;

import first.wildfires.Wildfires;
import first.wildfires.client.space.OrbitDimensionEffects;
import first.wildfires.client.space.SpaceClientState;
import first.wildfires.client.space.render.OrbitSkyRenderer;
import first.wildfires.thirdparty.genesisadapt.GenesisPlanetShader;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client MOD-bus registration for the unified sky, stars, and managed shaders. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CelestialClientEvents {

    private CelestialClientEvents() {}

    @SubscribeEvent
    public static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
        SpaceClientState.install();
        event.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new WildfiresOverworldEffects());
        event.register(Wildfires.rl("orbit"), new OrbitDimensionEffects());
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(StarDataManager.INSTANCE);
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                resourceManager -> {
                    // The server-authored station snapshot remains valid across F3+T; only visual caches reload.
                    CelestialClientStateCache.reset();
                    if (RenderSystem.isOnRenderThread()) {
                        OrbitSkyRenderer.close();
                    } else {
                        RenderSystem.recordRenderCall(OrbitSkyRenderer::close);
                    }
                });
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        AuroraRenderer.registerShader(event);
        LunarEclipseRenderer.registerShader(event);
        GenesisPlanetShader.registerShaders(event);
    }
}
