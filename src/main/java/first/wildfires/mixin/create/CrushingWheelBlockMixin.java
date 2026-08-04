package first.wildfires.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlock;
import com.tterrag.registrate.util.entry.BlockEntry;
import first.wildfires.block.CustomCrushingWheelBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CrushingWheelBlock.class, remap = false)
public class CrushingWheelBlockMixin {

    @WrapOperation(
            method = "updateControllers(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z"
            )
    )
    public boolean updateControllersCheckInstance(BlockEntry<?> instance, BlockState state, Operation<Boolean> original) {
        if (state.getBlock() instanceof CustomCrushingWheelBlock) {
            return true;
        }
        return original.call(instance, state);
    }

    @WrapOperation(
            // This inherited vanilla method must be remapped through the
            // refmap: canSurvive in userdev is m_7898_ in the packaged jar.
            method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z",
                    remap = false
            ),
            remap = true
    )
    public boolean canSurviveCheckInstance(BlockEntry<?> instance, BlockState state, Operation<Boolean> original) {
        if (state.getBlock() instanceof CustomCrushingWheelBlock) {
            return true;
        }
        return original.call(instance, state);
    }

}
