package first.wildfires.register;

import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockModel;
import com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import first.wildfires.Wildfires;
import first.wildfires.block.CustomCogWheelBlock;
import first.wildfires.block.CustomDirectionalBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

import static com.simibubi.create.foundation.data.ModelGen.customItemModel;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;
import static net.minecraft.world.level.block.Block.box;

public class BlockRegister {

    private static final CreateRegistrate Registrate = Wildfires.Registrate;

    public static final BlockEntry<CustomCogWheelBlock> StoneCogWheel =
            Registrate.block("stone_cogwheel", properties -> new CustomCogWheelBlock(PartialModelRegister.StoneCogWheel, false, properties))
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK))
                    .transform(axeOrPickaxe())
                    .onRegister(CreateRegistrate.blockModel(() -> BracketedKineticBlockModel::new))
                    .item(CogwheelBlockItem::new)
                    .transform(customItemModel())
                    .register();

    public static final BlockEntry<CustomCogWheelBlock> StoneLargeCogWheel =
            Registrate.block("stone_large_cogwheel", properties -> new CustomCogWheelBlock(PartialModelRegister.StoneLargeCogWheel, true, properties))
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK))
                    .transform(axeOrPickaxe())
                    .onRegister(CreateRegistrate.blockModel(() -> BracketedKineticBlockModel::new))
                    .item(CogwheelBlockItem::new)
                    .transform(customItemModel())
                    .register();

    public static final DeferredRegister<Block> Register = DeferredRegister.create(Registries.BLOCK, Wildfires.MODID);

    public static final RegistryObject<SlabBlock> GrassSlab = Register.register("grass_slab", () -> new SlabBlock(BlockBehaviour.Properties.copy(Blocks.GRASS_BLOCK)));

    // 水下涡轮
    public static final RegistryObject<Block> UnderwaterTurbine = Register.register("underwater_turbine", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = Shapes.block();
        VoxelShape south = Shapes.block();
        VoxelShape west = Shapes.block();
        VoxelShape east = Shapes.block();
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 潜艇核心
    public static final RegistryObject<Block> SubmarineCore = Register.register("submarine_core", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.COPPER)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = Shapes.block();
        VoxelShape south = Shapes.block();
        VoxelShape west = Shapes.block();
        VoxelShape east = Shapes.block();
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 简易气球
    public static final RegistryObject<Block> SimpleAirCushion = Register.register("simple_air_cushion", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.WOOL)
                .strength(5.0f)
                .noOcclusion();
        VoxelShape north = Shapes.block();
        VoxelShape south = Shapes.block();
        VoxelShape west = Shapes.block();
        VoxelShape east = Shapes.block();
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 气球
    public static final RegistryObject<Block> AirCushion = Register.register("air_cushion", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.WOOL)
                .strength(5.0f)
                .noOcclusion();
        VoxelShape north = Shapes.block();
        VoxelShape south = Shapes.block();
        VoxelShape west = Shapes.block();
        VoxelShape east = Shapes.block();
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 双翼
    public static final RegistryObject<Block> DoubleWing = Register.register("double_wing", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.SCAFFOLDING)
                .strength(5.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(0, 3, 6, 16, 14, 26).optimize();
        VoxelShape south = box(0, 3, 6, 16, 14, 26).optimize();
        VoxelShape west = box(0, 3, 6, 16, 14, 26).optimize();
        VoxelShape east = box(0, 3, 6, 16, 14, 26).optimize();
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 飞艇侧板
    public static final RegistryObject<Block> AirshipSlats = Register.register("airship_slats", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.SCAFFOLDING)
                .strength(5.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(0, 4, 14, 16, 13, 16);
        VoxelShape south = box(0, 4, 14, 16, 13, 16);
        VoxelShape west = box(0, 4, 14, 16, 13, 16);
        VoxelShape east = box(0, 4, 14, 16, 13, 16);
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 人力飞机扇叶
    public static final RegistryObject<Block> GyrodynePropeller = Register.register("gyrodyne_propeller", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.SCAFFOLDING)
                .strength(5.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(7.75, -16, 7.75, 8.25, 16, 8.25);
        VoxelShape south = box(7.75, -16, 7.75, 8.25, 16, 8.25);
        VoxelShape west = box(7.75, -16, 7.75, 8.25, 16, 8.25);
        VoxelShape east = box(7.75, -16, 7.75, 8.25, 16, 8.25);
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 双翼机引擎
    public static final RegistryObject<Block> BiplaneEngine = Register.register("biplane_engine", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(0, 0, 0, 16, 16, 16);
        VoxelShape south = box(0, 0, 0, 16, 16, 16);
        VoxelShape west = box(0, 0, 0, 16, 16, 16);
        VoxelShape east = box(0, 0, 0, 16, 16, 16);
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 大型飞艇引擎
    public static final RegistryObject<Block> LargeAirshipEngine = Register.register("large_airship_engine", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(-1, 0, -2, 17, 18, 16);
        VoxelShape south = box(-1, 0, -2, 17, 18, 16);
        VoxelShape west = box(-1, 0, -2, 17, 18, 16);
        VoxelShape east = box(-1, 0, -2, 17, 18, 16);
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 小型引擎
    public static final RegistryObject<Block> SmallEngine = Register.register("small_engine", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(1, 0, 0, 15, 16, 16);
        VoxelShape south = box(1, 0, 0, 15, 16, 16);
        VoxelShape west = box(1, 0, 0, 15, 16, 16);
        VoxelShape east = box(1, 0, 0, 15, 16, 16);
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 小型侧引擎
    public static final RegistryObject<Block> SmallSideEngine = Register.register("small_side_engine", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(2, 2, 0, 14, 14, 16);
        VoxelShape south = box(2, 2, 0, 14, 14, 16);
        VoxelShape west = box(2, 2, 0, 14, 14, 16);
        VoxelShape east = box(2, 2, 0, 14, 14, 16);
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 坚固小型引擎
    public static final RegistryObject<Block> RuggedSmallEngine = Register.register("rugged_small_engine", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(1, 0, 0, 15, 16, 16);
        VoxelShape south = box(1, 0, 0, 15, 16, 16);
        VoxelShape west = box(1, 0, 0, 15, 16, 16);
        VoxelShape east = box(1, 0, 0, 15, 16, 16);
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 大型螺旋桨
    public static final RegistryObject<Block> LargePropeller = Register.register("large_propeller", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(5, 6, 12.5, 11, 12, 16.5);
        VoxelShape south = box(5, 6, 12.5, 11, 12, 16.5);
        VoxelShape west = box(5, 6, 12.5, 11, 12, 16.5);
        VoxelShape east = box(5, 6, 12.5, 11, 12, 16.5);
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 大型双螺旋桨
    public static final RegistryObject<Block> LargeTwinPropeller = Register.register("large_twin_propeller", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(5, 5, 12.5, 11, 11, 16.5);
        VoxelShape south = box(5, 5, 12.5, 11, 11, 16.5);
        VoxelShape west = box(5, 5, 12.5, 11, 11, 16.5);
        VoxelShape east = box(5, 5, 12.5, 11, 11, 16.5);
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 中型螺旋桨
    public static final RegistryObject<Block> MediumPropeller = Register.register("medium_propeller", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(5, 5, 12.5, 11, 11, 16.5);
        VoxelShape south = box(5, 5, 12.5, 11, 11, 16.5);
        VoxelShape west = box(5, 5, 12.5, 11, 11, 16.5);
        VoxelShape east = box(5, 5, 12.5, 11, 11, 16.5);
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 小型螺旋桨
    public static final RegistryObject<Block> SmallPropeller = Register.register("small_propeller", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(5, 5, 12.5, 11, 11, 16.5);
        VoxelShape south = box(5, 5, 12.5, 11, 11, 16.5);
        VoxelShape west = box(5, 5, 12.5, 11, 11, 16.5);
        VoxelShape east = box(5, 5, 12.5, 11, 11, 16.5);
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    public static void register(IEventBus eventBus) {
        Register.register(eventBus);
    }

}
