package first.wildfires.mixin.alex;

import com.github.alexmodguy.alexscaves.server.block.GuanoLayerBlock;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GuanoLayerBlock.class, remap = false)
public class GuanoLayerBlockMixin {

    @ModifyExpressionValue(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;of()Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;"
            )
    )
    private static BlockBehaviour.Properties injectRandomTicks(BlockBehaviour.Properties original) {
        return original.noCollission();
    }

}
