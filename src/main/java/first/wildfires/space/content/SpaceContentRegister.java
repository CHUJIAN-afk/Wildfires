package first.wildfires.space.content;

import first.wildfires.Wildfires;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(
            ForgeRegistries.MENU_TYPES, Wildfires.MODID);

    public static final RegistryObject<Block> STATION_CONTROL_COMPUTER = BLOCKS.register(
            "station_control_computer", () -> new StationControlComputerBlock(
                    BlockBehaviour.Properties.of().strength(4.0F, 8.0F)
                            .sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STATION_TEST_ENGINE = BLOCKS.register(
            "station_test_engine", () -> new StationTestEngineBlock(
                    BlockBehaviour.Properties.of().strength(4.0F, 8.0F)
                            .sound(SoundType.METAL).requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> STATION_CONTROL_COMPUTER_ITEM = ITEMS.register(
            "station_control_computer", () -> new BlockItem(STATION_CONTROL_COMPUTER.get(),
                    new Item.Properties()));
    public static final RegistryObject<Item> STATION_TEST_ENGINE_ITEM = ITEMS.register(
            "station_test_engine", () -> new BlockItem(STATION_TEST_ENGINE.get(),
                    new Item.Properties()));

    public static final RegistryObject<BlockEntityType<StationControlComputerBlockEntity>>
            STATION_CONTROL_COMPUTER_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "station_control_computer", () -> BlockEntityType.Builder.of(
                    StationControlComputerBlockEntity::new, STATION_CONTROL_COMPUTER.get()).build(null));
    public static final RegistryObject<BlockEntityType<StationTestEngineBlockEntity>>
            STATION_TEST_ENGINE_BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "station_test_engine", () -> BlockEntityType.Builder.of(
                    StationTestEngineBlockEntity::new, STATION_TEST_ENGINE.get()).build(null));

    public static final RegistryObject<MenuType<StationControlMenu>> STATION_CONTROL_MENU = MENUS.register(
            "station_control", () -> IForgeMenuType.create(StationControlMenu::new));

    private SpaceContentRegister() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
