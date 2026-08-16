package first.wildfires.client.space;

import com.github.alexthe666.citadel.ClientProxy;
import first.wildfires.Wildfires;
import first.wildfires.space.content.SpaceContentRegister;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/** Client-only menu registration for the station control computer. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpaceContentClientEvents {

    private SpaceContentClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(SpaceContentRegister.STATION_CONTROL_MENU.get(), StationControlScreen::new);
            if (!FMLEnvironment.production) {
                ClientProxy.hideFollower = true;
            }
        });
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SpaceContentRegister.REUSABLE_RETURN_CAPSULE.get(),
                ReusableReturnCapsuleRenderer::new);
        event.registerBlockEntityRenderer(SpaceContentRegister.STATION_CORE_BLOCK_ENTITY.get(),
                StationCoreRenderer::new);
        event.registerBlockEntityRenderer(SpaceContentRegister.ANTIMATTER_TEST_ENGINE_BLOCK_ENTITY.get(),
                AntimatterTestEngineRenderer::new);
        event.registerBlockEntityRenderer(SpaceContentRegister.DAEDALUS_V1_TEST_ENGINE_BLOCK_ENTITY.get(),
                WaterfallTestEngineRenderer::new);
        event.registerBlockEntityRenderer(SpaceContentRegister.DAEDALUS_V2_TEST_ENGINE_BLOCK_ENTITY.get(),
                WaterfallTestEngineRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(SpaceContentRegister.RETURN_CAPSULE_GAS_FLAME.get(),
                ReturnCapsuleGasFlameParticle.Provider::new);
        event.registerSpriteSet(SpaceContentRegister.RETURN_CAPSULE_SHOCK_SMOKE.get(),
                ReturnCapsuleShockSmokeParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        AntimatterRadiantDriveShader.register(event);
        WaterfallTranslatedEngineShader.register(event);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                resourceManager -> {
                    NtmSpaceObjModels.reset();
                    AntimatterRadiantDriveShader.reset();
                    WaterfallTranslatedEngineShader.reset();
                });
    }
}
