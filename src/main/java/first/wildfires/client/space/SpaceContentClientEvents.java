package first.wildfires.client.space;

import first.wildfires.Wildfires;
import first.wildfires.space.content.SpaceContentRegister;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

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
}
