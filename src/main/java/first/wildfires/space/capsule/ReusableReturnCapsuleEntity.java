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
import first.wildfires.space.content.StationIdTapeItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalInt;
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
    private static final EntityDataAccessor<ItemStack> NAVIGATION_TAPE = SynchedEntityData.defineId(
            ReusableReturnCapsuleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DOCK_LOCKED = SynchedEntityData.defineId(
            ReusableReturnCapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TIPPING_EXPLOSIVE = SynchedEntityData.defineId(
            ReusableReturnCapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> ACTIVE_BODY_ID = SynchedEntityData.defineId(
            ReusableReturnCapsuleEntity.class, EntityDataSerializers.STRING);
    /** Exact top-of-surface Y used by client ascent visuals and exhaust ground effects. */
    private static final EntityDataAccessor<Integer> SURFACE_REFERENCE_Y = SynchedEntityData.defineId(
            ReusableReturnCapsuleEntity.class, EntityDataSerializers.INT);
    /** NTM rocketVelocity is visual state as well as server motion and must cross the tracker. */
    private static final EntityDataAccessor<Float> FLIGHT_VELOCITY = SynchedEntityData.defineId(
            ReusableReturnCapsuleEntity.class, EntityDataSerializers.FLOAT);

    private final ReturnCapsuleFuelTank fuelTank = new ReturnCapsuleFuelTank(this::onFuelChanged);
    private LazyOptional<net.minecraftforge.fluids.capability.IFluidHandler> fluidCapability =
            LazyOptional.of(() -> fuelTank);
    private UUID ownerPlayer;
    private ReturnCapsuleTransitionTicket transitionTicket;
    private ResourceLocation homeSurfaceDimension;
    private BlockPos homeSurfacePosition;
    private boolean transferDismountInProgress;
    private boolean primaryActionArmed = true;
    private boolean primaryActionPressed;
    private int missingPassengerTicks;
    private Long flightTicketChunk;
    private double clientTargetX;
    private double clientTargetY;
    private double clientTargetZ;
    private float clientTargetYaw;
    private float clientTargetPitch;
    private int clientLerpSteps;

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
        entityData.define(NAVIGATION_TAPE, ItemStack.EMPTY);
        entityData.define(DOCK_LOCKED, false);
        entityData.define(TIPPING_EXPLOSIVE, false);
        entityData.define(ACTIVE_BODY_ID, "");
        entityData.define(SURFACE_REFERENCE_Y, Integer.MIN_VALUE);
        entityData.define(FLIGHT_VELOCITY, 0.0F);
    }

    public ReturnCapsuleState capsuleState() {
        return ReturnCapsuleState.fromStableId(entityData.get(STATE))
                .orElse(ReturnCapsuleState.RECOVERY_REQUIRED);
    }

    public void setCapsuleState(ReturnCapsuleState state) {
        if (state != ReturnCapsuleState.SURFACE_TIPPING) {
            entityData.set(TIPPING_EXPLOSIVE, false);
        }
        entityData.set(STATE, state.stableId());
        incrementRevision();
    }

    public void beginTipping(boolean explosive) {
        entityData.set(TIPPING_EXPLOSIVE, explosive);
        setPhaseTicks(0);
        setCapsuleState(ReturnCapsuleState.SURFACE_TIPPING);
    }

    public boolean tippingExplosive() {
        return entityData.get(TIPPING_EXPLOSIVE);
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
        return StationIdTapeItem.stationId(entityData.get(NAVIGATION_TAPE));
    }

    public void bindStation(UUID stationId) {
        setNavigationTape(StationIdTapeItem.createMigrated(stationId));
    }

    public void clearStationBinding() {
        setNavigationTape(ItemStack.EMPTY);
    }

    public ItemStack navigationTape() {
        return entityData.get(NAVIGATION_TAPE).copy();
    }

    public CompoundTag saveNavigationTapeForItem() {
        ItemStack tape = navigationTape();
        return tape.isEmpty() ? new CompoundTag() : tape.save(new CompoundTag());
    }

    public boolean loadNavigationTapeFromItem(CompoundTag tag) {
        ItemStack tape = ItemStack.of(tag);
        if (!tape.isEmpty() && (!tape.is(SpaceContentRegister.STATION_ID_TAPE.get())
                || StationIdTapeItem.stationId(tape).isEmpty())) return false;
        setNavigationTape(tape);
        return true;
    }

    public void setNavigationTape(ItemStack stack) {
        ItemStack normalized = stack.is(SpaceContentRegister.STATION_ID_TAPE.get())
                && StationIdTapeItem.stationId(stack).isPresent() ? stack.copyWithCount(1) : ItemStack.EMPTY;
        if (!ItemStack.matches(entityData.get(NAVIGATION_TAPE), normalized)) {
            entityData.set(NAVIGATION_TAPE, normalized);
            incrementRevision();
            if (!level().isClientSide() && !normalized.isEmpty()
                    && capsuleState() == ReturnCapsuleState.SURFACE_LANDED) {
                // NTM ItemVOTVdrive changes LANDED to AWAITING and starts the 45-tick door close.
                setPhaseTicks(0);
                setCapsuleState(ReturnCapsuleState.SURFACE_CLOSING);
            }
        }
    }

    public boolean dockLocked() {
        return entityData.get(DOCK_LOCKED);
    }

    public void setDockLocked(boolean locked) {
        if (entityData.get(DOCK_LOCKED) != locked) {
            entityData.set(DOCK_LOCKED, locked);
            incrementRevision();
        }
    }

    public Optional<ReturnCapsuleTransitionTicket> transitionTicket() {
        return Optional.ofNullable(transitionTicket);
    }

    public void setTransitionTicket(ReturnCapsuleTransitionTicket ticket) {
        transitionTicket = ticket;
        entityData.set(ACTIVE_BODY_ID, ticket.bodyId().toString());
        incrementRevision();
    }

    public void clearTransitionTicket() {
        if (transitionTicket != null) {
            transitionTicket = null;
            entityData.set(ACTIVE_BODY_ID, "");
            incrementRevision();
        }
    }

    /** Synchronized body identity used by the generic bound-surface ascent renderer. */
    public Optional<ResourceLocation> activeBodyId() {
        return Optional.ofNullable(ResourceLocation.tryParse(entityData.get(ACTIVE_BODY_ID)));
    }

    public void setHomeSurface(ResourceLocation dimension, BlockPos position) {
        homeSurfaceDimension = dimension;
        homeSurfacePosition = position.immutable();
        entityData.set(SURFACE_REFERENCE_Y, position.getY());
        incrementRevision();
    }

    public Optional<ResourceLocation> homeSurfaceDimension() {
        return Optional.ofNullable(homeSurfaceDimension);
    }

    public Optional<BlockPos> homeSurfacePosition() {
        return Optional.ofNullable(homeSurfacePosition);
    }

    /** Synchronized independently because transition tickets/home positions are server state. */
    public OptionalInt surfaceReferenceY() {
        int value = entityData.get(SURFACE_REFERENCE_Y);
        return value == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(value);
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

    /** Direct NTM canExitCapsule contract for every moving/transfer phase. */
    public boolean canExitCapsule() {
        ReturnCapsuleState state = capsuleState();
        return state == ReturnCapsuleState.SURFACE_LANDED
                || state == ReturnCapsuleState.SURFACE_CLOSING
                || state == ReturnCapsuleState.SURFACE_TIPPING
                || state == ReturnCapsuleState.STATION_DOCKED
                || state == ReturnCapsuleState.RECOVERY_REQUIRED;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        // The no-exit rule is authoritative only on the server. On the client Minecraft's
        // SetPassengers handler must be able to eject the old graph before rebuilding it after a
        // cross-dimension Respawn; blocking that temporary dismount strands the replacement
        // LocalPlayer outside the otherwise correctly tracked destination capsule.
        if (!level().isClientSide() && !transferDismountInProgress && !canExitCapsule()
                && !isRemoved() && !passenger.isRemoved()) {
            return;
        }
        super.removePassenger(passenger);
    }

    public boolean primaryActionArmed() {
        return primaryActionArmed;
    }

    public void armPrimaryAction() {
        primaryActionArmed = true;
    }

    public void disarmPrimaryAction() {
        primaryActionArmed = false;
    }

    public void recordPrimaryActionInput(boolean pressed) {
        primaryActionPressed = pressed;
        if (!pressed) {
            armPrimaryAction();
        }
    }

    public boolean primaryActionPressed() {
        return primaryActionPressed;
    }

    public int missingPassengerTicks() {
        return missingPassengerTicks;
    }

    public void resetMissingPassengerTicks() {
        missingPassengerTicks = 0;
    }

    public int incrementMissingPassengerTicks() {
        return ++missingPassengerTicks;
    }

    public double flightVelocity() {
        return entityData.get(FLIGHT_VELOCITY);
    }

    public void setFlightVelocity(double velocity) {
        entityData.set(FLIGHT_VELOCITY, (float) Mth.clamp(velocity, -4.0D, 4.0D));
    }

    public Optional<Long> flightTicketChunk() {
        return Optional.ofNullable(flightTicketChunk);
    }

    public void setFlightTicketChunk(@Nullable Long chunk) {
        flightTicketChunk = chunk;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()
                && getFirstPassenger() instanceof net.minecraft.server.level.ServerPlayer player) {
            boolean serverPressed = ReturnCapsuleService.primaryActionPressed(player);
            if (serverPressed != primaryActionPressed) {
                if (serverPressed) {
                    // A press observed while the player was temporarily detached is stale input,
                    // not a new riding edge. Consume it without launching after the remount.
                    primaryActionPressed = true;
                    disarmPrimaryAction();
                } else {
                    recordPrimaryActionInput(false);
                }
            }
        }
        if (!level().isClientSide() && transitionTicket != null) {
            ReturnCapsuleService.tick(this);
            return;
        }
        if (!level().isClientSide() && transitionTicket == null && capsuleState().interactive()
                && phaseTicks() < 100) {
            setPhaseTicks(phaseTicks() + 1);
        }
        if (!level().isClientSide() && transitionTicket == null
                && capsuleState() == ReturnCapsuleState.SURFACE_TIPPING
                && ReturnCapsuleService.tickTipping(this)) {
            return;
        }
        if (level().isClientSide()) {
            ReturnCapsuleVisuals.spawnClientParticles(this);
        }
        if (level().isClientSide() && !capsuleState().interactive()) {
            tickClientInterpolation();
            return;
        }
        setDeltaMovement(getDeltaMovement().multiply(0.8D, 1.0D, 0.8D));
        if (!isNoGravity() && !onGround()) {
            setDeltaMovement(getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        }
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps,
                       boolean teleport) {
        if (!level().isClientSide() || capsuleState().interactive() || teleport) {
            super.lerpTo(x, y, z, yaw, pitch, steps, teleport);
            return;
        }
        clientTargetX = x;
        clientTargetY = y;
        clientTargetZ = z;
        clientTargetYaw = yaw;
        clientTargetPitch = pitch;
        // Exact modern equivalent of NTM EntityThrowableInterp#setPositionAndRotation2: retain the
        // tracker-provided approach count (normally three) instead of snapping every position packet
        // in one tick. The camera and rider renderer consume this same interpolated vehicle frame.
        clientLerpSteps = Math.max(1, steps);
    }

    private void tickClientInterpolation() {
        if (clientLerpSteps <= 0) return;
        double divisor = clientLerpSteps;
        setPos(getX() + (clientTargetX - getX()) / divisor,
                getY() + (clientTargetY - getY()) / divisor,
                getZ() + (clientTargetZ - getZ()) / divisor);
        setYRot(getYRot() + Mth.wrapDegrees(clientTargetYaw - getYRot()) / clientLerpSteps);
        setXRot(getXRot() + (clientTargetPitch - getXRot()) / clientLerpSteps);
        clientLerpSteps--;
    }

    public boolean transferProtected() {
        return transitionTicket != null
                && transitionTicket.stage() != ReturnCapsuleTransitionTicket.Stage.COMMITTED;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (transferProtected()) return false;
        if (!level().isClientSide() && source.getEntity() instanceof Player player
                && getPassengers().isEmpty() && capsuleState().interactive()) {
            return ReturnCapsuleService.tryRecoverAsItem(player, this);
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
            return ReturnCapsuleService.applyStationTape(player, this, hand, held);
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
            if (player.startRiding(this)) {
                // Require a real post-mount release before accepting NTM's AWAITING jump edge.
                primaryActionPressed = ReturnCapsuleService.primaryActionPressed(
                        (net.minecraft.server.level.ServerPlayer) player);
                if (!primaryActionPressed) armPrimaryAction(); else disarmPrimaryAction();
            }
            player.displayClientMessage(Component.translatable("space.wildfires.return_capsule.controls"), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return (capsuleState().interactive() || transferProtected()) && getPassengers().isEmpty();
    }

    @Override
    protected boolean couldAcceptPassenger() {
        // Entity.startRiding(vehicle, true) still calls this capacity gate before the force flag
        // bypasses canAddPassenger. The authoritative transition ticket is server-only state, so a
        // newly tracked destination capsule cannot use transferProtected() to accept the replacement
        // LocalPlayer on the client. Client acceptance only permits the vanilla SetPassengers graph
        // to converge; the server retains the complete interactive/transfer ticket gate.
        return level().isClientSide() || capsuleState().interactive() || transferProtected();
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (hasPassenger(passenger)) {
            // Direct modern equivalent of NTM updateRiderPosition: the astronaut follows the
            // capsule attitude instead of floating away from the seat as launch pitch increases.
            double length = CAPSULE_SEAT_OFFSET;
            double pitch = Math.toRadians(getXRot() - 90.0F);
            double yaw = Math.toRadians(180.0F - getYRot());
            double x = -Math.sin(yaw) * Math.cos(pitch) * length;
            double y = -Math.sin(pitch) * length;
            double z = Math.cos(yaw) * Math.cos(pitch) * length;
            move.accept(passenger, getX() + x, getY() + y, getZ() + z);
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (!level().isClientSide() && capsuleState() == ReturnCapsuleState.STATION_DOCKED) {
            return ReturnCapsuleService.dockedCorePosition(this)
                    .map(ReturnCapsuleService::stationDismountPosition)
                    .orElseGet(() -> super.getDismountLocationForPassenger(passenger));
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // NTM's reusable seat baseline, lowered another half block after in-game model alignment.
    // This single axial anchor drives server position, third person and the first-person camera.
    public static final double CAPSULE_SEAT_OFFSET = ReturnCapsuleService.CAPSULE_HEIGHT - 3.0D;

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
        ItemStack tape = tag.get("navigation_tape") instanceof CompoundTag tapeTag
                ? ItemStack.of(tapeTag) : ItemStack.EMPTY;
        if (tape.isEmpty() && tag.contains("station_id", Tag.TAG_INT_ARRAY)) {
            tape = StationIdTapeItem.createMigrated(tag.getUUID("station_id"));
        }
        entityData.set(NAVIGATION_TAPE, tape);
        entityData.set(DOCK_LOCKED, tag.contains("dock_locked", Tag.TAG_BYTE)
                ? tag.getBoolean("dock_locked") : state == ReturnCapsuleState.STATION_DOCKED);
        entityData.set(TIPPING_EXPLOSIVE, state == ReturnCapsuleState.SURFACE_TIPPING
                && tag.contains("tipping_explosive", Tag.TAG_BYTE)
                && tag.getBoolean("tipping_explosive"));
        primaryActionArmed = !tag.contains("primary_action_armed", Tag.TAG_BYTE)
                || tag.getBoolean("primary_action_armed");
        primaryActionPressed = tag.contains("primary_action_pressed", Tag.TAG_BYTE)
                ? tag.getBoolean("primary_action_pressed") : !primaryActionArmed;
        entityData.set(FLIGHT_VELOCITY, tag.contains("flight_velocity", Tag.TAG_DOUBLE)
                ? (float) Mth.clamp(tag.getDouble("flight_velocity"), -4.0D, 4.0D) : 0.0F);
        flightTicketChunk = tag.contains("flight_ticket_chunk", Tag.TAG_LONG)
                ? tag.getLong("flight_ticket_chunk") : null;
        transitionTicket = tag.get("transition_ticket") instanceof CompoundTag ticketTag
                ? ReturnCapsuleTransitionTicket.load(ticketTag) : null;
        entityData.set(ACTIVE_BODY_ID, transitionTicket == null
                ? "" : transitionTicket.bodyId().toString());
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
        entityData.set(SURFACE_REFERENCE_Y, homeSurfacePosition == null
                ? Integer.MIN_VALUE : homeSurfacePosition.getY());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("state", capsuleState().stableId());
        tag.put("fuel", fuelTank.save());
        tag.putLong("revision", revision());
        tag.putInt("phase_ticks", phaseTicks());
        if (ownerPlayer != null) tag.putUUID("owner_player", ownerPlayer);
        if (!navigationTape().isEmpty()) tag.put("navigation_tape", navigationTape().save(new CompoundTag()));
        tag.putBoolean("dock_locked", dockLocked());
        if (capsuleState() == ReturnCapsuleState.SURFACE_TIPPING) {
            tag.putBoolean("tipping_explosive", tippingExplosive());
        }
        tag.putBoolean("primary_action_armed", primaryActionArmed);
        tag.putBoolean("primary_action_pressed", primaryActionPressed);
        tag.putDouble("flight_velocity", flightVelocity());
        if (flightTicketChunk != null) tag.putLong("flight_ticket_chunk", flightTicketChunk);
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
