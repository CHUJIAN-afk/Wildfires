package first.wildfires.mixin.create;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import first.wildfires.block.CustomCrushingWheelBlock;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CrushingWheelControllerBlock.class,remap = false)
public class CrushingWheelControllerBlockMixin {

    @Inject(
            method = "updateSpeed",
            at = @At("HEAD"),
            cancellable = true
    )
    public void updateSpeedCheckInstance(BlockState state, LevelAccessor world, BlockPos pos, CallbackInfo ci) {
        CrushingWheelControllerBlock crushingWheelControllerBlock = (CrushingWheelControllerBlock) state.getBlock();
        crushingWheelControllerBlock.withBlockEntityDo(world, pos, (be) -> {
            if (!(Boolean) state.getValue(CrushingWheelControllerBlock.VALID)) {
                if (be.crushingspeed != 0.0F) {
                    be.crushingspeed = 0.0F;
                    be.sendData();
                }
            } else {
                for (Direction d : Iterate.directions) {
                    BlockState neighbour = world.getBlockState(pos.relative(d));
                    if ((neighbour.getBlock() instanceof CustomCrushingWheelBlock || AllBlocks.CRUSHING_WHEEL.has(neighbour)) && neighbour.getValue(BlockStateProperties.AXIS) != d.getAxis()) {
                        BlockEntity adjBE = world.getBlockEntity(pos.relative(d));
                        if (adjBE instanceof CrushingWheelBlockEntity cwbe) {
                            be.crushingspeed = Math.abs(cwbe.getSpeed() / 50.0F);
                            be.sendData();
                            cwbe.award(AllAdvancements.CRUSHING_WHEEL);
                            if (cwbe.getSpeed() > 255.0F) {
                                cwbe.award(AllAdvancements.CRUSHER_MAXED);
                            }
                            break;
                        }
                    }
                }

            }
        });
        ci.cancel();
    }

}
