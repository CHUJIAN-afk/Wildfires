package first.wildfires.mixin.embeddium;

import first.wildfires.client.TfcLeavesCulling;
import net.dries007.tfc.common.blocks.wood.ILeavesBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache", remap = false)
public class BlockOcclusionCacheMixin {
    private static final ThreadLocal<BlockPos.MutableBlockPos> SCRATCH_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Inject(method = "shouldDrawSide", at = @At("RETURN"), cancellable = true, remap = false)
    private void preserveSecondTfcLeafLayer(BlockState state, BlockGetter level, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!TfcLeavesCulling.useFastLeaves() || !(state.getBlock() instanceof ILeavesBlock)) {
            return;
        }

        BlockPos.MutableBlockPos scratch = SCRATCH_POS.get();
        scratch.set(pos).move(direction);
        if (!(level.getBlockState(scratch).getBlock() instanceof ILeavesBlock)) {
            return;
        }

        // The outer layer draws normally. Draw this shared face only for the second layer.
        scratch.move(direction);
        cir.setReturnValue(!(level.getBlockState(scratch).getBlock() instanceof ILeavesBlock));
    }
}
