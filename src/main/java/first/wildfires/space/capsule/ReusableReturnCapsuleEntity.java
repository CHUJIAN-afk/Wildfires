/*
 * Adapted from NTM: Space EntityRideableRocket reusable rp_pod_20 behavior.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: rebuilt as a single Forge 1.20.1 entity with synchronized stable
 * phases, persisted passenger/station/endpoints, Forge water capability and protected transfer
 * lifetime; removed modular stages, weapons and NTM's custom fluid system, while replacing its
 * swappable coordinate navigation drive with the station-UUID tape contract.
 */
package first.wildfires.space.capsule;

import first.wildfires.space.content.SpaceContentRegister;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** The sole first-release shuttle: a persisted, rideable NTM-style reusable return capsule. */
public final class ReusableReturnCapsuleEntity extends Entity {

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(
            ReusableReturnCapsuleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FUEL_MB = SynchedEntityData.defineId(
            ReusableReturnCapsuleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> REVISION = SynchedEntityData.defineId(
            ReusableReturnCapsuleEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> PHASE_TICKS = SynchedEntityData.defineId(
            ReusableReturnCapsuleEntity.class, EntityDataSerializers.INT);

    private final ReturnCapsuleFuelTank fuelTank = new ReturnCapsuleFuelTank(this::onFuelChanged);
    private LazyOptional<net.minecraftforge.fluids.capability.IFluidHandler> fluidCapability =
            LazyOptional.of(() -> fuelTank);
    private UUID ownerPlayer;
    private UUID stationId;
    private ReturnCapsuleTransitionTicket transitionTicket;
    private ResourceLocation homeSurfaceDimension;
    private BlockPos homeSurfacePosition;
    private boolean transferDismountInProgress;

    public ReusableReturnCapsuleEntity(EntityType<? extends ReusableReturnCapsuleEntity> type, Level level) {
        super(type, level);
        blocksBuilding = true;
    }

    public ReusableReturnCapsuleEntity(Level level, double x, double y, double z, UUID ownerPlayer) {
        this(SpaceContentRegister.REUSABLE_RETURN_CAPSULE.get(), level);
        this.ownerPlayer = ownerPlayer;
        setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STATE, ReturnCapsuleState.SURFACE_LANDED.stableId());
        entityData.define(FUEL_MB, 0);
        entityData.define(REVISION, 0L);
        entityData.define(PHASE_TICKS, 0);
    }

    public ReturnCapsuleState capsuleState() {
        return ReturnCapsuleState.fromStableId(entityData.get(STATE))
                .orElse(ReturnCapsuleState.RECOVERY_REQUIRED);
    }

    public void setCapsuleState(ReturnCapsuleState state) {
        entityData.set(STATE, state.stableId());
        incrementRevision();
    }

    public int fuelMb() {
        return entityData.get(FUEL_MB);
    }

    public long revision() {
        return entityData.get(REVISION);
    }

    public int phaseTicks() {
        return entityData.get(PHASE_TICKS);
    }

    public void setPhaseTicks(int ticks) {
        entityData.set(PHASE_TICKS, Math.max(0, ticks));
    }

    public ReturnCapsuleFuelTank fuelTank() {
        return fuelTank;
    }

    public void initializeFuelForTesting(int amountMb) {
        if (amountMb < 0 || amountMb > ReturnCapsuleFuelTank.CAPACITY_MB || fuelTank.storedMb() != 0
                || fuelTank.reservation().isPresent()) {
            throw new IllegalStateException("Return capsule test fuel can only initialize an empty tank");
        }
        int accepted = fuelTank.fill(new FluidStack(Fluids.WATER, amountMb),
                net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        if (accepted != amountMb) {
            throw new IllegalStateException("Return capsule test fuel was not accepted atomically");
        }
    }

    public boolean reserveFuelTrip(UUID ticketId) {
        boolean reserved = fuelTank.reserveTrip(ticketId);
        if (reserved) syncFuelAndRevision();
        return reserved;
    }

    public boolean commitFuelTrip(UUID ticketId) {
        int before = fuelTank.storedMb();
        boolean committed = fuelTank.commit(ticketId);
        if (committed && fuelTank.storedMb() == before) {
            // An already committed ticket is a successful idempotent replay, not another mutation.
            entityData.set(FUEL_MB, fuelTank.storedMb());
        }
        return committed;
    }

    public boolean rollbackFuelTrip(UUID ticketId) {
        boolean rolledBack = fuelTank.rollback(ticketId);
        if (rolledBack) syncFuelAndRevision();
        return rolledBack;
    }

    public Optional<UUID> ownerPlayer() {
        return Optional.ofNullable(ownerPlayer);
    }

    public Optional<UUID> stationId() {
        return Optional.ofNullable(stationId);
    }

    public void bindStation(UUID stationId) {
        if (!stationId.equals(this.stationId)) {
            this.stationId = stationId;
            incrementRevision();
        }
    }

    public void clearStationBinding() {
        if (stationId != null) {
            stationId = null;
            incrementRevision();
        }
    }

    public Optional<ReturnCapsuleTransitionTicket> transitionTicket() {
        return Optional.ofNullable(transitionTicket);
    }

    public void setTransitionTicket(ReturnCapsuleTransitionTicket ticket) {
        transitionTicket = ticket;
        incrementRevision();
    }

    public void clearTransitionTicket() {
        if (transitionTicket != null) {
            transitionTicket = null;
            incrementRevision();
        }
    }

    public void setHomeSurface(ResourceLocation dimension, BlockPos position) {
        homeSurfaceDimension = dimension;
        homeSurfacePosition = position.immutable();
        incrementRevision();
    }

    public Optional<ResourceLocation> homeSurfaceDimension() {
        return Optional.ofNullable(homeSurfaceDimension);
    }

    public Optional<BlockPos> homeSurfacePosition() {
        return Optional.ofNullable(homeSurfacePosition);
    }

    /** Internal guard used only while the service atomically moves the vehicle/passenger pair. */
    public void beginTransferDismount() {
        transferDismountInProgress = true;
    }

    public void endTransferDismount() {
        transferDismountInProgress = false;
    }

    public boolean transferDismountInProgress() {
        return transferDismountInProgress;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && transitionTicket != null) {
            ReturnCapsuleService.tick(this);
            return;
        }
        if (level().isClientSide() && !capsuleState().interactive()) {
            // The server sends a position every tick. Client-side re-integration of the previous
            // velocity would move once before that authoritative trajectory and visibly oscillate.
            return;
        }
        setDeltaMovement(getDeltaMovement().multiply(0.8D, 1.0D, 0.8D));
        if (!isNoGravity() && !onGround()) {
            setDeltaMovement(getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        }
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
    }

    public boolean transferProtected() {
        return transitionTicket != null
                && transitionTicket.stage() != ReturnCapsuleTransitionTicket.Stage.COMMITTED;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (transferProtected()) return false;
        if (!level().isClientSide() && source.getEntity() instanceof Player
                && getPassengers().isEmpty() && capsuleState().interactive()) {
            return ReturnCapsuleService.tryRecoverAsItem(this);
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return transferProtected() || super.isInvulnerableTo(source);
    }

    @Override
    public boolean ignoreExplosion() {
        return transferProtected() || super.ignoreExplosion();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (transferProtected() && reason != RemovalReason.CHANGED_DIMENSION
                && reason != RemovalReason.UNLOADED_TO_CHUNK
                && reason != RemovalReason.UNLOADED_WITH_PLAYER) {
            return;
        }
        super.remove(reason);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!capsuleState().interactive()) {
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(SpaceContentRegister.STATION_ID_TAPE.get())) {
            return ReturnCapsuleService.applyStationTape(player, this, held);
        }
        if (held.is(Items.WATER_BUCKET)) {
            if (level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            FluidStack bucket = new FluidStack(Fluids.WATER, ReturnCapsuleFuelTank.TRIP_COST_MB);
            if (fuelTank.fill(bucket, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE)
                    != ReturnCapsuleFuelTank.TRIP_COST_MB) {
                return InteractionResult.CONSUME;
            }
            int filled = fuelTank.fill(bucket,
                    net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            if (filled != ReturnCapsuleFuelTank.TRIP_COST_MB) {
                throw new IllegalStateException("Simulated return-capsule bucket fill could not be executed");
            }
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            // FluidTank.onContentsChanged performs the authoritative sync.
            return InteractionResult.CONSUME;
        }
        if (!level().isClientSide() && !player.isPassenger() && getPassengers().isEmpty()) {
            player.startRiding(this);
            player.displayClientMessage(Component.translatable("space.wildfires.return_capsule.controls"), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return capsuleState().interactive() && getPassengers().isEmpty();
    }

    @Override
    protected boolean couldAcceptPassenger() {
        return capsuleState().interactive() || transferProtected();
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (hasPassenger(passenger)) {
            move.accept(passenger, getX(), getY() + 1.15D, getZ());
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.75D;
    }

    @Override
    protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 2.4F;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ReturnCapsuleState state = ReturnCapsuleState.fromStableId(tag.getInt("state"))
                .orElse(ReturnCapsuleState.RECOVERY_REQUIRED);
        entityData.set(STATE, state.stableId());
        fuelTank.load(tag.getCompound("fuel"));
        entityData.set(FUEL_MB, fuelTank.storedMb());
        entityData.set(REVISION, Math.max(0L, tag.getLong("revision")));
        entityData.set(PHASE_TICKS, Math.max(0, tag.getInt("phase_ticks")));
        ownerPlayer = tag.contains("owner_player", Tag.TAG_INT_ARRAY) ? tag.getUUID("owner_player") : null;
        stationId = tag.contains("station_id", Tag.TAG_INT_ARRAY) ? tag.getUUID("station_id") : null;
        transitionTicket = tag.get("transition_ticket") instanceof CompoundTag ticketTag
                ? ReturnCapsuleTransitionTicket.load(ticketTag) : null;
        homeSurfaceDimension = tag.contains("home_surface_dimension", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("home_surface_dimension")) : null;
        homeSurfacePosition = tag.contains("home_surface_x", Tag.TAG_INT)
                && tag.contains("home_surface_y", Tag.TAG_INT)
                && tag.contains("home_surface_z", Tag.TAG_INT)
                ? new BlockPos(tag.getInt("home_surface_x"), tag.getInt("home_surface_y"),
                tag.getInt("home_surface_z")) : null;
        if ((homeSurfaceDimension == null) != (homeSurfacePosition == null)) {
            homeSurfaceDimension = null;
            homeSurfacePosition = null;
            entityData.set(STATE, ReturnCapsuleState.RECOVERY_REQUIRED.stableId());
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("state", capsuleState().stableId());
        tag.put("fuel", fuelTank.save());
        tag.putLong("revision", revision());
        tag.putInt("phase_ticks", phaseTicks());
        if (ownerPlayer != null) tag.putUUID("owner_player", ownerPlayer);
        if (stationId != null) tag.putUUID("station_id", stationId);
        if (transitionTicket != null) tag.put("transition_ticket", transitionTicket.save());
        if (homeSurfaceDimension != null && homeSurfacePosition != null) {
            tag.putString("home_surface_dimension", homeSurfaceDimension.toString());
            tag.putInt("home_surface_x", homeSurfacePosition.getX());
            tag.putInt("home_surface_y", homeSurfacePosition.getY());
            tag.putInt("home_surface_z", homeSurfacePosition.getZ());
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable net.minecraft.core.Direction side) {
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
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
        fluidCapability = LazyOptional.of(() -> fuelTank);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private void syncFuelAndRevision() {
        entityData.set(FUEL_MB, fuelTank.storedMb());
        incrementRevision();
    }

    private void onFuelChanged() {
        entityData.set(FUEL_MB, fuelTank.storedMb());
        incrementRevision();
    }

    private void incrementRevision() {
        long current = entityData.get(REVISION);
        entityData.set(REVISION, current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1L);
    }

    public CompoundTag saveFuelForItem() {
        return fuelTank.save();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    public boolean loadFuelFromItem(CompoundTag tag) {
        try {
            fuelTank.load(tag);
            entityData.set(FUEL_MB, fuelTank.storedMb());
            incrementRevision();
            return fuelTank.reservation().isEmpty();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
