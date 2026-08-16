package first.wildfires.space.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** Shared NTM propulsion state for the Daedalus v1 and v2 test blocks. */
public final class WaterfallTestEngineBlockEntity extends BlockEntity implements StationPropulsion {

    private static final int STARTUP_TICKS = 60;
    private static final int SHUTDOWN_TICKS = 20;
    private static final int SHUTDOWN_STEP = STARTUP_TICKS / SHUTDOWN_TICKS;
    private static final float THRUST = 3_000_000.0F;
    private int rampTicks;
    private int output;
    private boolean hasRegistered;
    private final WaterfallTestEngineVariant variant;

    public WaterfallTestEngineBlockEntity(BlockPos pos, BlockState state) {
        this(WaterfallTestEngineVariant.fromState(state), pos, state);
    }

    WaterfallTestEngineBlockEntity(WaterfallTestEngineVariant variant, BlockPos pos,
                                   BlockState state) {
        super(variant.blockEntityType().get(), pos, state);
        this.variant = variant;
    }

    public static void tick(Level level, BlockPos pos, BlockState state,
                            WaterfallTestEngineBlockEntity engine) {
        if (level.isClientSide) return;
        if (!engine.hasRegistered) engine.hasRegistered = StationDriveIndex.register(engine);
        int nextRampTicks = state.getValue(WaterfallTestEngineBlock.ON)
                ? Math.min(STARTUP_TICKS, engine.rampTicks + 1)
                : Math.max(0, engine.rampTicks - SHUTDOWN_STEP);
        if (nextRampTicks == engine.rampTicks) return;
        engine.rampTicks = nextRampTicks;
        engine.output = outputForRamp(nextRampTicks);
        engine.setChanged();
        level.sendBlockUpdated(pos, state, state, 2);
    }

    public int output() {
        return output;
    }

    public WaterfallTestEngineVariant variant() {
        return variant;
    }

    @Override
    public AABB getRenderBoundingBox() {
        boolean v1 = variant == WaterfallTestEngineVariant.DAEDALUS_V1;
        int radius = v1 ? 76 : 6;
        return new AABB(worldPosition.offset(-radius, -radius, 0),
                worldPosition.offset(radius + 1, radius + 1, v1 ? 350 : 140));
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
        return THRUST;
    }

    @Override
    public int startBurn() {
        setBurning(true);
        return STARTUP_TICKS;
    }

    @Override
    public int endBurn() {
        setBurning(false);
        return SHUTDOWN_TICKS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("ramp_ticks", rampTicks);
        tag.putInt("output", output);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("ramp_ticks", Tag.TAG_INT)) {
            rampTicks = Math.max(0, Math.min(STARTUP_TICKS, tag.getInt("ramp_ticks")));
        } else {
            int legacyOutput = Math.max(0, Math.min(100, tag.getInt("output")));
            rampTicks = Math.round(legacyOutput * STARTUP_TICKS / 100.0F);
        }
        output = outputForRamp(rampTicks);
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

    private void setBurning(boolean burning) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (state.getValue(WaterfallTestEngineBlock.ON) != burning) {
            level.setBlock(worldPosition, state.setValue(WaterfallTestEngineBlock.ON, burning), 3);
        }
    }

    private static int outputForRamp(int ticks) {
        return Math.round(ticks * 100.0F / STARTUP_TICKS);
    }

    private void unregisterPropulsion() {
        if (!hasRegistered) return;
        StationDriveIndex.unregister(this);
        hasRegistered = false;
    }
}
