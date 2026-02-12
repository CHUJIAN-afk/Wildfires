package first.wildfires.api;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

public record KineticData(
        Block block,
        Integer maxSpeed,
        Integer maxNetworkStress,
        List<ItemStack> list
) {

}
