package first.wildfires.event.forgeEvent;


import first.wildfires.Wildfires;
import first.wildfires.register.PartialModelRegister;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.MOD,value = Dist.CLIENT)
public class ClientModEvent {

    @SubscribeEvent
    public static void clientSetup(final FMLClientSetupEvent event) {
        PartialModelRegister.register();
    }

}
