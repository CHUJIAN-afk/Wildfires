package first.wildfires.register;

import com.github.alexthe666.citadel.server.item.CustomArmorMaterial;
import first.wildfires.Wildfires;
import first.wildfires.item.GeckoSimpleArmorItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.*;
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
//时装



    public static final RegistryObject<Item> COSMETIC =
            Register.register("cosmetic_tunmou_helmet",() -> new DyeableArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET,new Item.Properties())


            );


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

    public static final CustomArmorMaterial CosmeticArmorTunmou = new CustomArmorMaterial(
            "wildfires:tunmou_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性
    );
    public static final CustomArmorMaterial CosmeticArmoryuyu = new CustomArmorMaterial(
            "wildfires:yuyu_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性
    );
    public static final CustomArmorMaterial CosmeticArmorbeizi = new CustomArmorMaterial(
            "wildfires:beizi_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性
    );
    public static final CustomArmorMaterial CosmeticArmorhuacao = new CustomArmorMaterial(
            "wildfires:huacao_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性
    );

    public static final CustomArmorMaterial CosmeticArmormushroom = new CustomArmorMaterial(
            "wildfires:mushroom_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性

    );
    public static final CustomArmorMaterial CosmeticArmorxk = new CustomArmorMaterial(
            "wildfires:xk_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性

    );


    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticmushroomHelmet =
            Register.register("decoration/mushroom_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmormushroom,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));
    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticmushroomChestplat =
            Register.register("decoration/mushroom_armor_chestplate", () -> new GeckoSimpleArmorItem(
                    CosmeticArmormushroom,//护甲材料
                    ArmorItem.Type.CHESTPLATE,//头盔
                    new Item.Properties()//属性
            ));




    public static final RegistryObject<GeckoSimpleArmorItem> CosmetichuacaoHelmet =
            Register.register("decoration/huacao_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorhuacao,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));
    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticyuyuHelmet =
            Register.register("decoration/yuyu_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmoryuyu,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));
    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticbeiziHelmet =
            Register.register("decoration/beizi_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorbeizi,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));
    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticbeiziChestplat =
            Register.register("decoration/beizi_armor_chestplate", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorbeizi,//护甲材料
                    ArmorItem.Type.CHESTPLATE,//头盔
                    new Item.Properties()//属性
            ));

    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticTunmouHelmet =
            Register.register("decoration/tunmou_armor_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorTunmou,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));

    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticTunmouChestplat =
            Register.register("decoration/tunmou_armor_chestplate", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorTunmou,//护甲材料
                    ArmorItem.Type.CHESTPLATE,//头盔
                    new Item.Properties()//属性
            ));
    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticTunmouLeggings =
            Register.register("decoration/tunmou_armor_leggings", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorTunmou,//护甲材料
                    ArmorItem.Type.LEGGINGS,//头盔
                    new Item.Properties()//属性
            ));

    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticTunmouBoots =
            Register.register("decoration/tunmou_armor_boots", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorTunmou,//护甲材料
                    ArmorItem.Type.BOOTS,//头盔
                    new Item.Properties()//属性
            ));


    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticxkHelmet =
            Register.register("decoration/xk_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorxk,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));


    public static void register(IEventBus eventBus) {
        Register.register(eventBus);
    }

}
