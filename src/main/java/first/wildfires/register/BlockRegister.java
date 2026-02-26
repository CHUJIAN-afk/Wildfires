package first.wildfires.register;

import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockModel;
import com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import first.wildfires.Wildfires;
import first.wildfires.block.CustomCogWheelBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import static com.simibubi.create.foundation.data.ModelGen.customItemModel;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;

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

    public static void register(IEventBus eventBus) {
        Register.register(eventBus);
    }

}
