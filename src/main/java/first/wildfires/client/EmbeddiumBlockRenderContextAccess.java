package first.wildfires.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side bridge implemented by the optional Embeddium render-context mixin.
 */
public interface EmbeddiumBlockRenderContextAccess {
    BlockState wildfires$getState();

    BlockGetter wildfires$getLocalSlice();

    BlockPos wildfires$getPos();
}
