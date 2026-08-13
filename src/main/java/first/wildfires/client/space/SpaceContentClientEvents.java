package first.wildfires.client.space;

import first.wildfires.Wildfires;
import first.wildfires.space.content.SpaceContentRegister;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

/** Client-only menu registration for the station control computer. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpaceContentClientEvents {

    private SpaceContentClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(
                SpaceContentRegister.STATION_CONTROL_MENU.get(), StationControlScreen::new));
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SpaceContentRegister.REUSABLE_RETURN_CAPSULE.get(),
                ReusableReturnCapsuleRenderer::new);
        event.registerBlockEntityRenderer(SpaceContentRegister.STATION_CORE_BLOCK_ENTITY.get(),
                StationCoreRenderer::new);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("return_capsule_transition", ReturnCapsuleTransitionOverlay.INSTANCE);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                resourceManager -> NtmSpaceObjModels.reset());
    }
}
