package first.wildfires.mixin.tfc;

import net.dries007.tfc.common.blocks.plant.fruit.FruitTreeLeavesBlock;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FruitTreeLeavesBlock.class)
public class FruitTreeLeavesBlockMixin {
    // Mixin removed - let leaves render all faces for proper transparency
}
