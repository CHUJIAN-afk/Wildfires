package first.wildfires.register;

import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockModel;
import com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import first.wildfires.Wildfires;
import first.wildfires.block.CustomCogWheelBlock;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

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




    public static void register() {

    }

}
