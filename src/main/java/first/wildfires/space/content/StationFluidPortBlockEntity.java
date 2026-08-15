/*
 * Adapted from NTM: Space TileEntityOrbitalStation's twelve external fluid connections.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: exposes no-tick Forge 1.20.1 fluid capability proxies that forward
 * only Minecraft water into the physically present, bidirectionally locked reusable capsule.
 */
package first.wildfires.space.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One of NTM OrbitalStation's twelve no-tick external fluid connection proxies. */
public final class StationFluidPortBlockEntity extends BlockEntity {

    private static final String CORE_POSITION = "core_position";
    private BlockPos corePosition;
    private final IFluidHandler forwardingHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            IFluidHandler target = target();
            return target == null ? 1 : target.getTanks();
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            IFluidHandler target = target();
            return target == null ? FluidStack.EMPTY : target.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler target = target();
            return target == null
                    ? first.wildfires.space.capsule.ReturnCapsuleFuelTank.CAPACITY_MB
                    : target.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            IFluidHandler target = target();
            return target != null && target.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler target = target();
            return target == null ? 0 : target.fill(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> forwardingHandler);

    public StationFluidPortBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContentRegister.STATION_FLUID_PORT_BLOCK_ENTITY.get(), pos, state);
    }

    public void bindCore(BlockPos corePosition) {
        BlockPos normalized = corePosition.immutable();
        if (normalized.equals(this.corePosition)) return;
        this.corePosition = normalized;
        setChanged();
    }

    public @Nullable BlockPos corePosition() {
        return corePosition;
    }

    private @Nullable IFluidHandler target() {
        if (level == null || level.isClientSide() || corePosition == null
                || !level.getBlockState(corePosition).is(SpaceContentRegister.STATION_CORE.get())
                || !(level.getBlockEntity(corePosition) instanceof StationCoreBlockEntity core)) {
            return null;
        }
        return core.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                       @Nullable Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER
                && getBlockState().getValue(StationStructureBlock.FLUID_PORT)) {
            return fluidCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        fluidCapability = LazyOptional.of(() -> forwardingHandler);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (corePosition != null) tag.putLong(CORE_POSITION, corePosition.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        corePosition = tag.contains(CORE_POSITION, Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong(CORE_POSITION)) : null;
    }
}
