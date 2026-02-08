package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEvent {

    @SubscribeEvent
    public static void GuiRenderEvent(RenderGuiOverlayEvent.Pre event) {
        if (Wildfires.LSOLoaded && event.getOverlay().id().equals(ResourceLocation.parse("legendarysurvivaloverhaul:health_overhaul"))) {
            event.setCanceled(true);
        }
    }

}
