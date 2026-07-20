package first.wildfires.mixin.tfc;

import net.dries007.tfc.common.blockentities.TFCChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TFCChestBlockEntity.class)
public class TFCChestBlockEntityMixin {
    @Inject(method = "setBlockState", at = @At("TAIL"))
    private void invalidatePairedChestCapability(BlockState state, CallbackInfo ci) {
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return;
        }

        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }

        Direction direction = ChestBlock.getConnectedDirection(state);
        BlockPos neighborPos = self.getBlockPos().relative(direction);
        if (level.getBlockEntity(neighborPos) instanceof TFCChestBlockEntity neighbor) {
            neighbor.invalidateCaps();
        }
    }
}
