package first.wildfires.space.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Lifecycle hook for the loaded-engine index; it stores no inventory, tank or energy. */
public final class StationTestEngineBlockEntity extends BlockEntity implements StationPropulsion {

    private boolean hasRegistered;

    public StationTestEngineBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContentRegister.STATION_TEST_ENGINE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                            StationTestEngineBlockEntity engine) {
        if (!level.isClientSide && !engine.hasRegistered) {
            engine.hasRegistered = StationDriveIndex.register(engine);
        }
    }

    @Override
    public void onChunkUnloaded() {
        unregisterPropulsion();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterPropulsion();
        super.setRemoved();
    }

    @Override
    public BlockEntity blockEntity() {
        return this;
    }

    @Override
    public boolean canPerformBurn(int shipMass, double deltaV) {
        return true;
    }

    @Override
    public float thrust() {
        return 10_000_000.0F;
    }

    @Override
    public int startBurn() {
        return 20;
    }

    @Override
    public int endBurn() {
        return 20;
    }

    private void unregisterPropulsion() {
        if (hasRegistered) {
            StationDriveIndex.unregister(this);
            hasRegistered = false;
        }
    }
}
