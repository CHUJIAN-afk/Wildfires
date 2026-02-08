package first.wildfires.register;

import first.wildfires.Wildfires;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ItemRegister {

    private static final DeferredRegister<Item> Register = DeferredRegister.create(Registries.ITEM, Wildfires.MODID);

    public static final RegistryObject<Item> DragonFruitItem =
            Register.register("dragon_fruit", () -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .nutrition(3)
                            .saturationMod(0.7f)
                            .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200), 0.2f)
                            .build())
            ));

    public static final RegistryObject<Item> CopperBoltItem =
            Register.register("copper_bolt", () -> new Item(new Item.Properties()));

    public static void register(IEventBus bus) {
        Register.register(bus);
    }

}
