package first.wildfires.mixin.alex;

import com.github.alexmodguy.alexscaves.server.block.GuanoLayerBlock;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GuanoLayerBlock.class, remap = false)
public class GuanoLayerBlockMixin {

    @Inject(
            method = "getCollisionShape",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void getCollisionShape(BlockState state, BlockGetter level, BlockPos blockPos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir){
        cir.setReturnValue(Shapes.empty());
    }

}
