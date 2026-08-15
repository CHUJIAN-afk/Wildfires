package first.wildfires.space.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Maintains the synchronized 0..100 radiation-drive output and its ordinary-drive registration. */
public final class AntimatterTestEngineBlockEntity extends BlockEntity {

    private static final int OUTPUT_STEP = 5;
    private int output;

    public AntimatterTestEngineBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContentRegister.ANTIMATTER_TEST_ENGINE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AntimatterTestEngineBlockEntity engine) {
        if (level.isClientSide) {
            return;
        }
        int target = state.getValue(AntimatterTestEngineBlock.ON) ? 100 : 0;
        int next = Math.max(0, Math.min(100, engine.output + Integer.signum(target - engine.output) * OUTPUT_STEP));
        if (next != engine.output) {
            engine.output = next;
            engine.setChanged();
            level.sendBlockUpdated(pos, state, state, 2);
        }
    }

    public int output() {
        return output;
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

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("output", output);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        output = Math.max(0, Math.min(100, tag.getInt("output")));
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
