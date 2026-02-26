package first.wildfires.event.forgeEvent;


import first.wildfires.Wildfires;
import first.wildfires.register.BlockRegister;
import first.wildfires.register.PartialModelRegister;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.MOD,value = Dist.CLIENT)
public class ClientModEvent {

    @SubscribeEvent
    public static void clientSetup(final FMLClientSetupEvent event) {
        PartialModelRegister.register();
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
                    if (level != null && pos != null) {
                        return event.getBlockColors().getColor(Blocks.GRASS_BLOCK.defaultBlockState(), level, pos, tintIndex);
                    }
                    return 0x7c9c5c;
                },
                BlockRegister.GrassSlab.get()
        );
    }

}
