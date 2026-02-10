package first.wildfires.api;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

public record KineticData(
        BlockEntityType<? extends KineticBlockEntity> blockEntityType,
        Integer maxSpeed,
        Integer maxNetworkStress,
        List<ItemStack> list
) {

}
