package first.wildfires.space.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Lifecycle hook for the loaded-engine index; it stores no inventory, tank or energy. */
public final class StationTestEngineBlockEntity extends BlockEntity {

    public StationTestEngineBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContentRegister.STATION_TEST_ENGINE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        StationDriveIndex.register(this);
    }

    @Override
    public void onChunkUnloaded() {
        StationDriveIndex.unregister(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        StationDriveIndex.unregister(this);
        super.setRemoved();
    }
}
