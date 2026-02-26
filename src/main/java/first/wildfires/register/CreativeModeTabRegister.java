package first.wildfires.register;

import first.wildfires.Wildfires;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CreativeModeTabRegister {

    private static final DeferredRegister<CreativeModeTab> Register = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Wildfires.MODID);

    public static final RegistryObject<CreativeModeTab> WildfiresTab = Register.register("wildfires_tab", () -> CreativeModeTab.builder().icon(() -> Blocks.CAMPFIRE.asItem().getDefaultInstance())
            .title(Component.translatable("itemGroup.wildfire_tab"))
            .icon(Items.CAMPFIRE::getDefaultInstance)
            .displayItems((parameters, output) -> ForgeRegistries.ITEMS.getValues().stream()
                    .filter(item -> {
                        ResourceLocation location = ForgeRegistries.ITEMS.getKey(item);
                        return location != null && location.getNamespace().equals(Wildfires.MODID);
                    })
                    .forEach(output::accept))
            .build()
    );

    public static void register(IEventBus eventBus) {
        Register.register(eventBus);
    }

}
