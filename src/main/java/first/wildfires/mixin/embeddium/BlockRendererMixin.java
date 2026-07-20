package first.wildfires.mixin.embeddium;

import first.wildfires.client.TfcLeavesCulling;
import first.wildfires.client.EmbeddiumBlockRenderContextAccess;
import net.dries007.tfc.common.blocks.plant.ShortGrassBlock;
import net.dries007.tfc.common.blocks.wood.ILeavesBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.dries007.tfc.common.blocks.wood.ILeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public class BlockRendererMixin {
    private static final ThreadLocal<BlockPos.MutableBlockPos> SCRATCH_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, remap = false)
    private void skipDenseTfcVegetation(@Coerce Object context, @Coerce Object buffers, CallbackInfo ci) {
        EmbeddiumBlockRenderContextAccess access = (EmbeddiumBlockRenderContextAccess) context;
        if (isDenseGrassCore(access.wildfires$getState(), access.wildfires$getLocalSlice(), access.wildfires$getPos())
            || isTfcLeafCore(access.wildfires$getState(), access.wildfires$getLocalSlice(), access.wildfires$getPos())) {
            ci.cancel();
        }
    }

    @SuppressWarnings("unused")
    private static boolean isDenseGrassCore(BlockState state, BlockGetter level, BlockPos pos) {
        if (!TfcLeavesCulling.useFastLeaves() || !(state.getBlock() instanceof ShortGrassBlock)) {
            return false;
        }

        BlockPos.MutableBlockPos scratch = SCRATCH_POS.get();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            scratch.set(pos).move(direction);
            if (!(level.getBlockState(scratch).getBlock() instanceof ShortGrassBlock)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTfcLeafCore(BlockState state, BlockGetter level, BlockPos pos) {
        if (!TfcLeavesCulling.useFastLeaves() || !(state.getBlock() instanceof ILeavesBlock)) {
            return false;
        }

        BlockPos.MutableBlockPos scratch = SCRATCH_POS.get();
        for (Direction direction : Direction.values()) {
            scratch.set(pos).move(direction);
            for (int distance = 0; distance < 2; distance++) {
                if (!(level.getBlockState(scratch).getBlock() instanceof ILeavesBlock)) {
                    return false;
                }
                scratch.move(direction);
            }
        }
        return true;
    }

}
