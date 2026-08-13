package first.wildfires.space.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Lifecycle hook for the loaded-only jump-engine index. */
public final class StationJumpTestEngineBlockEntity extends BlockEntity {

    public StationJumpTestEngineBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContentRegister.STATION_JUMP_TEST_ENGINE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        StationJumpDriveIndex.register(this);
    }

    @Override
    public void onChunkUnloaded() {
        StationJumpDriveIndex.unregister(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        StationJumpDriveIndex.unregister(this);
        super.setRemoved();
    }
}
