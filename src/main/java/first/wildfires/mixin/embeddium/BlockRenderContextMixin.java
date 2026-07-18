package first.wildfires.mixin.embeddium;

import first.wildfires.client.EmbeddiumBlockRenderContextAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext", remap = false)
public abstract class BlockRenderContextMixin implements EmbeddiumBlockRenderContextAccess {
    @Shadow(remap = false)
    public abstract BlockState state();

    @Shadow(remap = false)
    public abstract BlockAndTintGetter localSlice();

    @Shadow(remap = false)
    public abstract BlockPos pos();

    @Override
    public BlockState wildfires$getState() {
        return state();
    }

    @Override
    public BlockGetter wildfires$getLocalSlice() {
        return localSlice();
    }

    @Override
    public BlockPos wildfires$getPos() {
        return pos();
    }
}
