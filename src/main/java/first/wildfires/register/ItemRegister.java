package first.wildfires.register;

import first.wildfires.Wildfires;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
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

    public static final RegistryObject<Item> GrassSlab = Register.register("grass_slab", () -> new BlockItem(BlockRegister.GrassSlab.get(), new Item.Properties()));

    public static final RegistryObject<Item> UnderwaterTurbine =
            Register.register("underwater_turbine", () -> new BlockItem(BlockRegister.UnderwaterTurbine.get(), new Item.Properties()));

    public static final RegistryObject<Item> SubmarineCore =
            Register.register("submarine_core", () -> new BlockItem(BlockRegister.SubmarineCore.get(), new Item.Properties()));

    public static final RegistryObject<Item> SimpleAirCushion =
            Register.register("simple_air_cushion", () -> new BlockItem(BlockRegister.SimpleAirCushion.get(), new Item.Properties()));

    public static final RegistryObject<Item> AirCushion =
            Register.register("air_cushion", () -> new BlockItem(BlockRegister.AirCushion.get(), new Item.Properties()));

    public static final RegistryObject<Item> DoubleWing =
            Register.register("double_wing", () -> new BlockItem(BlockRegister.DoubleWing.get(), new Item.Properties()));

    public static final RegistryObject<Item> AirshipSlats =
            Register.register("airship_slats", () -> new BlockItem(BlockRegister.AirshipSlats.get(), new Item.Properties()));

    public static final RegistryObject<Item> GyrodynePropeller =
            Register.register("gyrodyne_propeller", () -> new BlockItem(BlockRegister.GyrodynePropeller.get(), new Item.Properties()));

    public static final RegistryObject<Item> BiplaneEngine =
            Register.register("biplane_engine", () -> new BlockItem(BlockRegister.BiplaneEngine.get(), new Item.Properties()));

    public static final RegistryObject<Item> LargeAirshipEngine =
            Register.register("large_airship_engine", () -> new BlockItem(BlockRegister.LargeAirshipEngine.get(), new Item.Properties()));

    public static final RegistryObject<Item> SmallEngine =
            Register.register("small_engine", () -> new BlockItem(BlockRegister.SmallEngine.get(), new Item.Properties()));

    public static final RegistryObject<Item> SmallSideEngine =
            Register.register("small_side_engine", () -> new BlockItem(BlockRegister.SmallSideEngine.get(), new Item.Properties()));

    public static final RegistryObject<Item> RuggedSmallEngine =
            Register.register("rugged_small_engine", () -> new BlockItem(BlockRegister.RuggedSmallEngine.get(), new Item.Properties()));

    public static final RegistryObject<Item> LargePropeller =
            Register.register("large_propeller", () -> new BlockItem(BlockRegister.LargePropeller.get(), new Item.Properties()));

    public static final RegistryObject<Item> LargeTwinPropeller =
            Register.register("large_twin_propeller", () -> new BlockItem(BlockRegister.LargeTwinPropeller.get(), new Item.Properties()));

    public static final RegistryObject<Item> MediumPropeller =
            Register.register("medium_propeller", () -> new BlockItem(BlockRegister.MediumPropeller.get(), new Item.Properties()));

    public static final RegistryObject<Item> SmallPropeller =
            Register.register("small_propeller", () -> new BlockItem(BlockRegister.SmallPropeller.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        Register.register(eventBus);
    }

}
