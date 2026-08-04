package first.wildfires.mixin.minecraft;

import first.wildfires.client.TfcLeavesCulling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(Block.class)
public class BlockShouldRenderFaceMixin {

    @ModifyExpressionValue(
            method = "shouldRenderFace",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;skipRendering(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z"
            )
    )
    private static boolean wildfires$cullInnerTfcLeaves(boolean sideInvisible, BlockState state,
                                                         BlockGetter level, BlockPos pos,
                                                         Direction direction, BlockPos adjacentPos) {
        return sideInvisible || TfcLeavesCulling.shouldCullLeafSide(state, level, pos, direction);
    }
}
