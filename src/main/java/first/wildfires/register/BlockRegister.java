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
import static net.minecraft.world.phys.shapes.Shapes.box;

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
        VoxelShape south = Shapes.or(
                box(0, 11, 0, 16, 16, 16),
                box(1, 4, 1, 15, 11, 15),
                box(1, 0, 1, 4, 4, 4),
                box(1, 0, 12, 4, 4, 15),
                box(12, 0, 1, 15, 4, 4),
                box(12, 0, 12, 15, 4, 15)
        ).optimize();
        VoxelShape west = Shapes.block();
        VoxelShape east = Shapes.block();
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });


    public static void register(IEventBus eventBus) {
        Register.register(eventBus);
    }

}
