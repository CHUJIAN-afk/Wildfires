package first.wildfires.mixin.minecraft;

import first.wildfires.kinetic.loom.LoomAuxiliaryBlock;
import first.wildfires.kinetic.loom.LoomControlBlock;
import first.wildfires.kinetic.loom.LoomStructureBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端Mixin：禁用织布机方块的破坏纹理显示
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(
            method = "destroyBlockProgress",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onDestroyBlockProgress(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        // 如果是织布机相关方块，取消破坏进度渲染
        if (block instanceof LoomControlBlock ||
            block instanceof LoomStructureBlock ||
            block instanceof LoomAuxiliaryBlock) {
            ci.cancel();
        }
    }
}
