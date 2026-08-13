package first.wildfires.space.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Optional;
import java.util.UUID;

/** Stores only the station identity; all mutable station state remains in SpaceSavedData. */
public final class StationCoreBlockEntity extends BlockEntity {

    private static final String STATION_ID = "station_id";
    private UUID stationId;

    public StationCoreBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContentRegister.STATION_CORE_BLOCK_ENTITY.get(), pos, state);
    }

    public Optional<UUID> stationId() {
        return Optional.ofNullable(stationId);
    }

    public void bind(UUID stationId) {
        if (stationId.equals(this.stationId)) {
            return;
        }
        this.stationId = stationId;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (stationId != null) {
            tag.putUUID(STATION_ID, stationId);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stationId = tag.contains(STATION_ID, Tag.TAG_INT_ARRAY) ? tag.getUUID(STATION_ID) : null;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-2, -1, -2), worldPosition.offset(3, 3, 3));
    }
}
