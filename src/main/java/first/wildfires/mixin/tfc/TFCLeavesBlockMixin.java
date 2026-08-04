package first.wildfires.mixin.tfc;

import net.dries007.tfc.common.blocks.wood.TFCLeavesBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TFCLeavesBlock.class)
public class TFCLeavesBlockMixin {

    public boolean m_6104_(BlockState state, BlockState adjacentState, Direction side) {
        return adjacentState.is((Block) (Object) this);
    }
}
