package first.wildfires.event.forgeEvent;


import first.wildfires.Wildfires;
import first.wildfires.client.renderer.entity.ReplacedBearRenderer;
import first.wildfires.ponder.WildfiresPonderPlugin;
import first.wildfires.register.BlockRegister;
import first.wildfires.register.PartialModelRegister;
import fr.lucreeper74.createmetallurgy.ponders.CMPonders;
import net.createmod.ponder.foundation.PonderIndex;
import net.dries007.tfc.common.entities.TFCEntities;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.MOD,value = Dist.CLIENT)
public class ClientModEvent {

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        PartialModelRegister.register();
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TFCEntities.BLACK_BEAR.get(), ReplacedBearRenderer::BlackBear);
        event.registerEntityRenderer(TFCEntities.POLAR_BEAR.get(), ReplacedBearRenderer::PolarBear);
        event.registerEntityRenderer(TFCEntities.GRIZZLY_BEAR.get(), ReplacedBearRenderer::GrizzlyBear);
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
