package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import first.wildfires.register.ItemRegister;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only item model properties. Never loaded by a dedicated server. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvent {

    private ClientModEvent() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ItemRegister.SimpleCompass.get(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "angle"),
                (ClampedItemPropertyFunction) ItemProperties.getProperty(Items.COMPASS,
                        ResourceLocation.fromNamespaceAndPath("minecraft", "angle"))
        ));
    }
}
