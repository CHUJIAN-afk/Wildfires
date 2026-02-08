package first.wildfires.register;

import first.wildfires.Wildfires;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collection;

public class CreativeModeTabRegister {

    private static final DeferredRegister<CreativeModeTab> Register = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Wildfires.MODID);

    public static final RegistryObject<CreativeModeTab> CreatePowerTab = Register.register("create_power_tab", () -> CreativeModeTab.builder().icon(() -> Blocks.CAMPFIRE.asItem().getDefaultInstance())
            .title(Component.translatable("itemGroup.wildfire_tab"))
            .icon(() -> BlockRegister.StoneCogWheel.asItem().getDefaultInstance())
            .displayItems((parameters, output) -> {
                IForgeRegistry<Item> registry = ForgeRegistries.ITEMS;
                Collection<Item> items = registry.getValues();
                for (Item item : items) {
                    ResourceLocation location = registry.getKey(item);
                    if (location != null && location.getNamespace().equals(Wildfires.MODID)) {
                        output.accept(item);
                    }
                }
            })
            .build()
    );

    public static void register(IEventBus eventBus) {
        Register.register(eventBus);
    }

}
