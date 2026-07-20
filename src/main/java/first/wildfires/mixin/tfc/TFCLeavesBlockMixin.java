package first.wildfires.mixin.tfc;

import net.dries007.tfc.common.blocks.wood.TFCLeavesBlock;
import net.dries007.tfc.common.blocks.wood.ILeavesBlock;
import first.wildfires.client.TfcLeavesCulling;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TFCLeavesBlock.class)
public class TFCLeavesBlockMixin {

    public boolean m_6104_(BlockState state, BlockState adjacentState, Direction side) {
        if (TfcLeavesCulling.useFastLeaves() && adjacentState.getBlock() instanceof ILeavesBlock) {
            return true;
        }
        return adjacentState.is((Block) (Object) this);
    }
}
