package first.wildfires.space.content;

import first.wildfires.Wildfires;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Isolated Forge registrations for the deliberately small first space-content slice. */
public final class SpaceContentRegister {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            ForgeRegistries.BLOCKS, Wildfires.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            ForgeRegistries.ITEMS, Wildfires.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            ForgeRegistries.BLOCK_ENTITY_TYPES, Wildfires.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            ForgeRegistries.ENTITY_TYPES, Wildfires.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(
            ForgeRegistries.MENU_TYPES, Wildfires.MODID);

    public static final RegistryObject<Block> STATION_CONTROL_COMPUTER = BLOCKS.register(
            "station_control_computer", () -> new StationControlComputerBlock(
                    BlockBehaviour.Properties.of().strength(4.0F, 8.0F)
                            .sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STATION_CORE = BLOCKS.register(
            "station_core", () -> new StationCoreBlock(
                    BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F)
                            .sound(SoundType.METAL).noLootTable()));
    public static final RegistryObject<Block> STATION_STRUCTURE = BLOCKS.register(
            "station_structure", () -> new StationStructureBlock(
                    BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F)
                            .sound(SoundType.METAL).noLootTable()));
    public static final RegistryObject<Block> STATION_TEST_ENGINE = BLOCKS.register(
            "station_test_engine", () -> new StationTestEngineBlock(
                    BlockBehaviour.Properties.of().strength(4.0F, 8.0F)
                            .sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STATION_JUMP_TEST_ENGINE = BLOCKS.register(
            "station_jump_test_engine", () -> new StationJumpTestEngineBlock(
                    BlockBehaviour.Properties.of().strength(4.0F, 8.0F)
                            .sound(SoundType.METAL).requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> STATION_CONTROL_COMPUTER_ITEM = ITEMS.register(
            "station_control_computer", () -> new BlockItem(STATION_CONTROL_COMPUTER.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> STATION_TEST_ENGINE_ITEM = ITEMS.register(
            "station_test_engine", () -> new BlockItem(STATION_TEST_ENGINE.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> STATION_JUMP_TEST_ENGINE_ITEM = ITEMS.register(
            "station_jump_test_engine", () -> new BlockItem(STATION_JUMP_TEST_ENGINE.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> REUSABLE_RETURN_CAPSULE_ITEM = ITEMS.register(
            "reusable_return_capsule", () -> new first.wildfires.space.capsule.ReusableReturnCapsuleItem(
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> STATION_ID_TAPE = ITEMS.register(
            "station_id_tape", () -> new StationIdTapeItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<EntityType<first.wildfires.space.capsule.ReusableReturnCapsuleEntity>>
            REUSABLE_RETURN_CAPSULE = ENTITIES.register("reusable_return_capsule",
            () -> EntityType.Builder.<first.wildfires.space.capsule.ReusableReturnCapsuleEntity>of(
                            first.wildfires.space.capsule.ReusableReturnCapsuleEntity::new,
                            MobCategory.MISC)
                    .sized(2.5F, 3.5625F).clientTrackingRange(12).updateInterval(1)
                    .build(Wildfires.rl("reusable_return_capsule").toString()));

    public static final RegistryObject<BlockEntityType<StationControlComputerBlockEntity>>
            STATION_CONTROL_COMPUTER_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "station_control_computer", () -> BlockEntityType.Builder.of(
                    StationControlComputerBlockEntity::new, STATION_CONTROL_COMPUTER.get()).build(null));
    public static final RegistryObject<BlockEntityType<StationCoreBlockEntity>>
            STATION_CORE_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "station_core", () -> BlockEntityType.Builder.of(
                    StationCoreBlockEntity::new, STATION_CORE.get()).build(null));
    public static final RegistryObject<BlockEntityType<StationTestEngineBlockEntity>>
            STATION_TEST_ENGINE_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "station_test_engine", () -> BlockEntityType.Builder.of(
                    StationTestEngineBlockEntity::new, STATION_TEST_ENGINE.get()).build(null));
    public static final RegistryObject<BlockEntityType<StationJumpTestEngineBlockEntity>>
            STATION_JUMP_TEST_ENGINE_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "station_jump_test_engine", () -> BlockEntityType.Builder.of(
                    StationJumpTestEngineBlockEntity::new, STATION_JUMP_TEST_ENGINE.get()).build(null));

    public static final RegistryObject<MenuType<StationControlMenu>> STATION_CONTROL_MENU = MENUS.register(
            "station_control", () -> IForgeMenuType.create(StationControlMenu::new));

    private SpaceContentRegister() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
