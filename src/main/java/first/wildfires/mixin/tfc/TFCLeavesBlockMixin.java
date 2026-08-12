package first.wildfires.mixin.tfc;

import net.dries007.tfc.common.blocks.wood.TFCLeavesBlock;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TFCLeavesBlock.class)
public class TFCLeavesBlockMixin {
    // Mixin removed - let leaves render all faces for proper transparency
}
