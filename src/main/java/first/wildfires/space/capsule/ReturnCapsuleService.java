/*
 * Adapted from NTM: Space EntityRideableRocket, CelestialTeleporter and
 * TileEntityOrbitalStation, with the Forge 1.20.1 vehicle-first level-transfer ordering adapted
 * from VS: Genesis EntityTeleporter.
 * Copyright NTM: Space contributors.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: replaced modular rockets, navigation drives and hydrazine with one
 * water-fuelled return capsule; ported launch/landing/docking/undocking state boundaries to Forge
 * 1.20.1; added UUID-preserving passenger transfer, exactly-once fuel tickets, crash recovery and
 * bounded source/target chunk tickets for uninterrupted insertion and re-entry.
 */
package first.wildfires.space.capsule;

import com.mojang.logging.LogUtils;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.celestial.CelestialRegistryRuntime;
import first.wildfires.space.celestial.CelestialDefinition;
import first.wildfires.space.celestial.CelestialDefinitionRegistry;
import first.wildfires.space.celestial.CelestialSurfaceBindingResolver;
import first.wildfires.space.celestial.CelestialTransferProfile;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.celestial.CelestialConfig;
import first.wildfires.space.content.SpaceContentRegister;
import first.wildfires.space.content.StationCoreBlockEntity;
import first.wildfires.space.content.StationIdTapeItem;
import first.wildfires.space.content.StationCoreService;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import first.wildfires.space.station.StationDockRecord;
import first.wildfires.space.station.StationService;
import first.wildfires.network.ReturnCapsuleTransitionPacket;
import first.wildfires.network.StationContextPacket;
import first.wildfires.network.ReturnCapsuleTrackingCommitPacket;
import first.wildfires.network.ReturnCapsuleTransitionCompletePacket;
import first.wildfires.network.ReturnCapsuleTransitionAbortPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;

/** Authoritative launch, transfer, docking and recovery service for the sole shuttle entity. */
public final class ReturnCapsuleService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PRIMARY_INPUT_PRESSED = "wildfires_return_capsule_primary_pressed";

    /** NTM EntityRideableRocket logical bounds for the height-3 rp_pod_20 capsule. */
    public static final double CAPSULE_HEIGHT = 4.0D;
    public static final double CAPSULE_HALF_WIDTH = 1.0D;
    /** EntityRideableRocket overrides EntityThrowableNT.motionMult() to four in every flight state. */
    public static final double NTM_MOTION_MULTIPLIER = 4.0D;
    /** NTM uses altitude, not a fixed duration; this value is only a presentation scale. */
    public static final int LAUNCH_TICKS = 360;
    public static final int DOOR_CLOSE_TICKS = 45;
    public static final int TRANSFER_HOLD_TICKS = 12;
    public static final float DOCK_YAW = -90.0F;
    public static final int INSERTION_TICKS = 20;
    /** Twenty-tick port wait plus a bounded twenty-four-block final approach at 0.4 block/tick. */
    public static final int APPROACH_TICKS = 80;
    public static final int UNDOCK_TICKS = 60;
    public static final int REENTRY_TICKS = 800;
    public static final int LANDING_TICKS = 900;
    public static final int TIPPING_EXPLOSION_TICKS = 95;
    private static final int TRANSFER_TICKET_RADIUS = 4;
    private static final int FLIGHT_TICKET_RADIUS = 2;
    private static final int TRANSFER_TICKET_TIMEOUT_TICKS = 6_000;
    private static final int PASSENGER_GRAPH_RETRY_TICKS = 5;
    private static final int SAFE_LANDING_SEARCH_RADIUS = 16;
    private static final TicketType<UUID> TRANSFER_TICKET = TicketType.create(
            "wildfires_return_capsule_transfer", Comparator.comparing(UUID::toString),
            TRANSFER_TICKET_TIMEOUT_TICKS);

    private ReturnCapsuleService() {
    }

    public static void recordPrimaryActionInput(ServerPlayer player, boolean pressed) {
        Objects.requireNonNull(player, "player");
        player.getPersistentData().putBoolean(PRIMARY_INPUT_PRESSED, pressed);
        if (player.getVehicle() instanceof ReusableReturnCapsuleEntity capsule) {
            capsule.recordPrimaryActionInput(pressed);
        }
    }

    static boolean primaryActionPressed(ServerPlayer player) {
        return player.getPersistentData().getBoolean(PRIMARY_INPUT_PRESSED);
    }

    /** Handles one client input edge exactly once and reports an action only for a press edge. */
    public static Optional<ActionResult> handlePrimaryActionInput(ServerPlayer player,
                                                                  ReusableReturnCapsuleEntity capsule,
                                                                  boolean pressed) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(capsule, "capsule");
        recordPrimaryActionInput(player, pressed);
        return pressed ? Optional.of(requestPrimaryActionRecorded(player, capsule)) : Optional.empty();
    }

    public static ActionResult requestPrimaryAction(ServerPlayer player, ReusableReturnCapsuleEntity capsule) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(capsule, "capsule");
        return handlePrimaryActionInput(player, capsule, true).orElseThrow();
    }

    private static ActionResult requestPrimaryActionRecorded(ServerPlayer player,
                                                              ReusableReturnCapsuleEntity capsule) {
        if (player.getVehicle() != capsule || capsule.getFirstPassenger() != player) {
            return ActionResult.NOT_RIDING;
        }
        if (capsule.transitionTicket().isPresent()) {
            // A synthetic press restored by the client while receiving a level cancels any
            // release seen earlier in this flight. It can never become a launch after commit.
            capsule.disarmPrimaryAction();
            return ActionResult.BUSY;
        }
        if (!capsule.primaryActionArmed()) {
            return ActionResult.BUSY;
        }
        ActionResult result = switch (capsule.capsuleState()) {
            case SURFACE_LANDED -> prepareSurfaceAwaiting(player, capsule);
            case SURFACE_CLOSING -> prepareLaunch(player, capsule);
            case STATION_DOCKED -> prepareReturn(player, capsule);
            case RECOVERY_REQUIRED -> ActionResult.RECOVERY_REQUIRED;
            default -> ActionResult.INVALID_STATE;
        };
        if (result == ActionResult.STARTED) capsule.disarmPrimaryAction();
        return result;
    }

    /**
     * NTM requires re-activating a drive after LANDED before AWAITING closes the door. Wildfires'
     * station tape lives inside the pod, so one fresh jump edge performs only that drive activation;
     * a release and second fresh edge are still required to ignite.
     */
    private static ActionResult prepareSurfaceAwaiting(ServerPlayer player,
                                                        ReusableReturnCapsuleEntity capsule) {
        UUID stationId = capsule.stationId().orElse(null);
        StationRecord station = stationId == null ? null
                : SpaceSavedData.get(player.server).station(stationId).orElse(null);
        if (station == null || !station.mayOperate(player.getUUID()) || station.journey().isPresent()) {
            return ActionResult.NO_STATION;
        }
        SurfaceBinding binding = resolveSurfaceBinding(player.server, station).orElse(null);
        if (binding == null
                || !binding.dimension().equals(player.serverLevel().dimension().location())) {
            return ActionResult.NO_SURFACE;
        }
        capsule.setPhaseTicks(0);
        capsule.setCapsuleState(ReturnCapsuleState.SURFACE_CLOSING);
        return ActionResult.STARTED;
    }

    /**
     * NTM only launches while the rider is actively jumping. 1.20.1 sends explicit release edges
     * so a world reload cannot manufacture a second launch from one held key.
     */
    public static void releasePrimaryAction(ServerPlayer player, ReusableReturnCapsuleEntity capsule) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(capsule, "capsule");
        recordPrimaryActionInput(player, false);
    }

    private static ActionResult prepareLaunch(ServerPlayer player, ReusableReturnCapsuleEntity capsule) {
        ResourceLocation surfaceDimension = player.serverLevel().dimension().location();
        UUID selected = capsule.stationId().orElse(null);
        StationRecord station = selected == null ? null
                : SpaceSavedData.get(player.server).station(selected).orElse(null);
        if (station == null || !station.mayOperate(player.getUUID()) || station.journey().isPresent()) {
            return ActionResult.NO_STATION;
        }
        SurfaceBinding binding = resolveSurfaceBinding(player.server, station).orElse(null);
        if (binding == null || !surfaceDimension.equals(binding.dimension())) return ActionResult.NO_SURFACE;
        // NTM's OrbitalStation owns a live rocket reference; a missing entity cannot leave its
        // port reserved forever. Modern block-entity UUID locks survive saves independently, so
        // reconcile them at the action boundary instead of making the operator wait for the
        // periodic station audit (or permanently rejecting a launch in an unloaded/stale save).
        reconcileDockLocks(player.server, station);
        StationDockRecord targetDock = availableDock(player.server, station, capsule.getUUID()).orElse(null);
        if (targetDock == null) return ActionResult.NO_DOCK;
        // Claim the physical port before reserving fuel or mutating the capsule. Server actions are
        // serialized, so this block-entity compare-and-set is the atomic arbitration point between
        // two simultaneous launches aimed at the same station.
        if (!reserveCoreDock(player.server, station, targetDock.position(), capsule.getUUID())) {
            return ActionResult.NO_DOCK;
        }
        UUID ticketId = UUID.randomUUID();
        if (!capsule.reserveFuelTrip(ticketId)) {
            releaseCoreDockLock(player.server, station, targetDock.position(), capsule.getUUID());
            return ActionResult.NO_FUEL;
        }
        ReturnCapsuleTransitionTicket ticket = new ReturnCapsuleTransitionTicket(ticketId,
                station.stationId(), ReturnCapsuleTransitionTicket.Direction.TO_STATION,
                station.currentBody(), surfaceDimension, capsule.blockPosition(),
                SpaceDimensions.ORBIT.location(), targetDock.position(), player.getUUID(),
                capsule.revision(), player.server.overworld().getGameTime(),
                ReturnCapsuleTransitionTicket.Stage.PREPARED);
        capsule.bindStation(station.stationId());
        capsule.setHomeSurface(surfaceDimension, capsule.blockPosition());
        capsule.setTransitionTicket(ticket);
        logTransition(capsule, ticket, "prepared surface-to-station trip");
        capsule.setPhaseTicks(0);
        capsule.setDockLocked(false);
        capsule.setFlightVelocity(0.0D);
        capsule.setCapsuleState(ReturnCapsuleState.SURFACE_LAUNCHING);
        StationService.OperationResult bound = StationService.setReturnCapsule(
                SpaceSavedData.get(player.server), station.stationId(),
                capsule.getUUID(), true, player.server.overworld().getGameTime());
        if (!bound.successful()) {
            releaseCoreDockLock(player.server, station, targetDock.position(), capsule.getUUID());
            capsule.rollbackFuelTrip(ticketId);
            capsule.clearTransitionTicket();
            // The tape selection existed before this launch attempt. A transient SavedData write
            // failure must roll back the trip without silently ejecting the selected destination.
            capsule.setCapsuleState(ReturnCapsuleState.SURFACE_LANDED);
            return ActionResult.NO_STATION;
        }
        acquireTripTickets(player.server, ticket);
        return ActionResult.STARTED;
    }

    public static InteractionResult applyStationTape(Player player,
                                                     ReusableReturnCapsuleEntity capsule,
                                                     InteractionHand hand,
                                                     ItemStack tape) {
        if (capsule.transferProtected()
                || (capsule.capsuleState() != ReturnCapsuleState.SURFACE_LANDED
                && capsule.capsuleState() != ReturnCapsuleState.SURFACE_CLOSING
                && capsule.capsuleState() != ReturnCapsuleState.STATION_DOCKED)) {
            if (!capsule.level().isClientSide()) {
                player.displayClientMessage(Component.translatable(
                        "space.wildfires.station_id_tape.not_surface_landed"), true);
            }
            return InteractionResult.CONSUME;
        }
        if (capsule.level().isClientSide()) return InteractionResult.SUCCESS;
        UUID stationId = StationIdTapeItem.stationId(tape).orElse(null);
        if (!(player instanceof ServerPlayer serverPlayer) || stationId == null) {
            player.displayClientMessage(Component.translatable(
                    "space.wildfires.station_id_tape.blank"), true);
            return InteractionResult.CONSUME;
        }
        StationRecord station = SpaceSavedData.get(serverPlayer.server).station(stationId).orElse(null);
        SurfaceBinding binding = station == null ? null
                : resolveSurfaceBinding(serverPlayer.server, station).orElse(null);
        ServerLevel orbit = level(serverPlayer.server, SpaceDimensions.ORBIT.location());
        boolean surfaceMatches = capsule.capsuleState() != ReturnCapsuleState.SURFACE_LANDED
                && capsule.capsuleState() != ReturnCapsuleState.SURFACE_CLOSING
                || binding != null && binding.dimension().equals(capsule.level().dimension().location());
        boolean dockMatches = capsule.capsuleState() != ReturnCapsuleState.STATION_DOCKED
                || station != null && station.ownedReturnCapsules().contains(capsule.getUUID())
                && dockForCapsule(serverPlayer.server, station, capsule.getUUID())
                .filter(dock -> capsule.position().distanceToSqr(stationDockedPosition(dock.position()))
                        < 0.0625D)
                .filter(dock -> validDockCore(serverPlayer.server, station, dock.position()))
                .filter(dock -> orbit != null
                        && orbit.getBlockEntity(dock.position()) instanceof StationCoreBlockEntity core
                        && core.dockedCapsuleId().filter(capsule.getUUID()::equals).isPresent()
                        && capsule.dockLocked()).isPresent();
        String tapeFailure = station == null ? "space.wildfires.station_id_tape.unknown_station"
                : binding == null ? "space.wildfires.station_id_tape.no_surface"
                : station.journey().isPresent() ? "space.wildfires.station_id_tape.station_travelling"
                : !(station.mayOperate(player.getUUID()) || player.hasPermissions(2))
                ? "space.wildfires.station_id_tape.no_permission"
                : !surfaceMatches ? "space.wildfires.station_id_tape.wrong_surface"
                : !dockMatches ? "space.wildfires.station_id_tape.wrong_dock" : null;
        if (tapeFailure != null) {
            player.displayClientMessage(Component.translatable(tapeFailure), true);
            return InteractionResult.CONSUME;
        }
        UUID previous = capsule.stationId().orElse(null);
        if (previous != null && !previous.equals(stationId)) {
            SpaceSavedData data = SpaceSavedData.get(serverPlayer.server);
            // A deleted/corrupt old station cannot retain an ownership entry, so its stale entity
            // UUID must not make the physical capsule impossible to reprogram.
            if (data.station(previous).isPresent()) {
                StationService.OperationResult unbound = StationService.setReturnCapsule(
                        data, previous, capsule.getUUID(), false,
                        serverPlayer.server.overworld().getGameTime());
                if (!unbound.successful()) {
                    player.displayClientMessage(Component.translatable(
                            "space.wildfires.station_id_tape.invalid"), true);
                    return InteractionResult.CONSUME;
                }
            }
        }
        ItemStack previousTape = capsule.navigationTape();
        capsule.setNavigationTape(tape.copyWithCount(1));
        if (capsule.capsuleState() == ReturnCapsuleState.SURFACE_LANDED) {
            // NTM ItemVOTVdrive changes LANDED to AWAITING; RenderDropPod then closes the door.
            capsule.setPhaseTicks(0);
            capsule.setCapsuleState(ReturnCapsuleState.SURFACE_CLOSING);
        }
        player.setItemInHand(hand, previousTape);
        player.displayClientMessage(Component.translatable(
                "space.wildfires.station_id_tape.applied", station.name()), true);
        return InteractionResult.CONSUME;
    }

    public static boolean tryRecoverAsItem(Player actor, ReusableReturnCapsuleEntity capsule) {
        if (!(capsule.level() instanceof ServerLevel level) || capsule.transferProtected()
                || !capsule.getPassengers().isEmpty() || !capsule.capsuleState().interactive()
                || capsule.transitionTicket().isPresent() || capsule.fuelTank().reservation().isPresent()) {
            return false;
        }
        SpaceSavedData data = SpaceSavedData.get(level.getServer());
        boolean owner = capsule.ownerPlayer().filter(actor.getUUID()::equals).isPresent();
        UUID stationId = capsule.stationId().orElse(null);
        StationRecord station = stationId == null ? null : data.station(stationId).orElse(null);
        if (!owner && !actor.hasPermissions(2)
                && (station == null || !station.mayOperate(actor.getUUID()))) return false;
        if (station != null) {
            if (capsule.capsuleState() == ReturnCapsuleState.STATION_DOCKED
                    && dockForCapsule(level.getServer(), station, capsule.getUUID())
                    .filter(dock -> releaseCoreDockLock(level.getServer(), station, dock.position(),
                            capsule.getUUID())).isEmpty()) return false;
            if (!StationService.setReturnCapsule(data, station.stationId(), capsule.getUUID(), false,
                    level.getServer().overworld().getGameTime()).successful()) return false;
        }
        ItemStack recovered = new ItemStack(SpaceContentRegister.REUSABLE_RETURN_CAPSULE_ITEM.get());
        recovered.getOrCreateTag().put("wildfires_capsule_fuel", capsule.saveFuelForItem());
        ItemStack navigationTape = capsule.navigationTape();
        capsule.spawnAtLocation(recovered, 0.0F);
        // NTM dropNDie drops the reusable pod and nav drive as two independent stacks. Keep old
        // embedded-tape item NBT readable for save compatibility, but all new break/recovery drops
        // expose the internal tape so it cannot be silently trapped or lost.
        if (!navigationTape.isEmpty()) capsule.spawnAtLocation(navigationTape, 0.0F);
        capsule.clearStationBinding();
        capsule.discard();
        return true;
    }

    private static ActionResult prepareReturn(ServerPlayer player, ReusableReturnCapsuleEntity capsule) {
        UUID stationId = capsule.stationId().orElse(null);
        if (stationId == null || player.serverLevel().dimension() != SpaceDimensions.ORBIT
                || !capsule.dockLocked()) {
            return ActionResult.NOT_DOCKED;
        }
        StationRecord station = SpaceSavedData.get(player.server).station(stationId).orElse(null);
        if (station == null || !station.mayOperate(player.getUUID()) || station.journey().isPresent()) {
            return ActionResult.NOT_DOCKED;
        }
        // Resolve the bidirectional reservation before requiring a complete 5x5x2 shell. This
        // distinguishes a genuinely foreign/unlocked capsule from a capsule whose own docking
        // core has been damaged, and—critically—keeps the lock and fuel untouched in both cases.
        StationDockRecord sourceDock = reservedDockForCapsule(
                player.server, station, capsule.getUUID()).orElse(null);
        if (sourceDock == null) {
            return ActionResult.NOT_DOCKED;
        }
        if (!validDockCore(player.server, station, sourceDock.position())) return ActionResult.NO_CORE;
        SurfaceBinding binding = resolveSurfaceBinding(player.server, station).orElse(null);
        if (binding == null) return ActionResult.NO_SURFACE;
        ResourceLocation surfaceDimension = binding.dimension();
        ResourceLocation savedSurfaceDimension = capsule.homeSurfaceDimension().orElse(null);
        BlockPos surface = capsule.homeSurfacePosition().orElse(null);
        if (!surfaceDimension.equals(savedSurfaceDimension) || surface == null
                || !isSafeLanding(binding.level(), surface)) {
            surface = findSafeLanding(binding.level()).orElse(null);
            if (surface == null) return ActionResult.NO_SURFACE;
            capsule.setHomeSurface(surfaceDimension, surface);
        }
        UUID ticketId = UUID.randomUUID();
        if (!capsule.reserveFuelTrip(ticketId)) return ActionResult.NO_FUEL;
        if (!releaseCoreDockLock(player.server, station, sourceDock.position(), capsule.getUUID())) {
            capsule.rollbackFuelTrip(ticketId);
            return ActionResult.NOT_DOCKED;
        }
        ReturnCapsuleTransitionTicket ticket = new ReturnCapsuleTransitionTicket(ticketId, stationId,
                ReturnCapsuleTransitionTicket.Direction.TO_SURFACE, station.currentBody(),
                SpaceDimensions.ORBIT.location(), sourceDock.position(), surfaceDimension, surface,
                player.getUUID(), capsule.revision(), player.server.overworld().getGameTime(),
                ReturnCapsuleTransitionTicket.Stage.PREPARED);
        capsule.setTransitionTicket(ticket);
        logTransition(capsule, ticket, "prepared station-to-surface trip");
        capsule.setPhaseTicks(0);
        capsule.setCapsuleState(ReturnCapsuleState.STATION_UNDOCKING);
        capsule.setDockLocked(false);
        acquireTripTickets(player.server, ticket);
        return ActionResult.STARTED;
    }

    public static void tick(ReusableReturnCapsuleEntity capsule) {
        if (!(capsule.level() instanceof ServerLevel source)) return;
        ReturnCapsuleTransitionTicket ticket = capsule.transitionTicket().orElse(null);
        if (ticket == null) return;
        if (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.COMMITTED) {
            capsule.clearTransitionTicket();
            capsule.setPhaseTicks(0);
            return;
        }
        if (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.RECOVERY) {
            // RECOVERY is a terminal fuse, not another phase to advance. Re-entering recover every
            // tick rewrites the same ticket/revision forever and can revive the transfer loop this
            // state exists to stop.
            capsule.setNoGravity(true);
            capsule.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.ROLLED_BACK) {
            capsule.clearTransitionTicket();
            capsule.setPhaseTicks(0);
            return;
        }
        long transactionAge = source.getServer().overworld().getGameTime() - ticket.createdGameTime();
        if (transactionAge > TRANSFER_TICKET_TIMEOUT_TICKS) {
            recover(capsule);
            return;
        }
        if (!reconcileTransferBoundary(capsule, source, ticket)) {
            return;
        }
        maintainFlightChunkTicket(capsule, source, ticket);
        int ticks = capsule.phaseTicks() + 1;
        capsule.setPhaseTicks(ticks);
        switch (capsule.capsuleState()) {
            case SURFACE_CLOSING -> {
                capsule.setNoGravity(true);
                capsule.setDeltaMovement(Vec3.ZERO);
                if (ticks > DOOR_CLOSE_TICKS) capsule.setPhaseTicks(DOOR_CLOSE_TICKS);
                // NTM AWAITING remains launchable after its 45-tick door close animation. It does
                // not self-launch when the animation ends.
            }
            case SURFACE_LAUNCHING -> {
                capsule.setNoGravity(true);
                tickNtmLaunch(capsule, ticks);
                double surfaceY = capsule.surfaceReferenceY().orElse(ticket.surfacePosition().getY());
                if (capsule.getY() - surfaceY > transferProfile(source.getServer(), ticket.bodyId())
                        .transferAltitude()) {
                    capsule.setFlightVelocity(0.0D);
                    advance(capsule, ReturnCapsuleState.ASCENT_TRANSITION);
                }
            }
            case ASCENT_TRANSITION -> {
                capsule.setNoGravity(true);
                capsule.setDeltaMovement(Vec3.ZERO);
                transferToStation(capsule, source, ticket);
            }
            case ORBIT_INSERTION -> {
                // Compatibility for development saves written by the old interpolation state.
                advance(capsule, ReturnCapsuleState.STATION_APPROACH);
            }
            case STATION_APPROACH -> {
                StationRecord station = SpaceSavedData.get(source.getServer()).station(ticket.stationId()).orElse(null);
                if (station == null) { recover(capsule); break; }
                Vec3 target = stationDockedPosition(ticket.targetPosition());
                capsule.setNoGravity(true);
                capsule.setYRot(DOCK_YAW);
                capsule.setXRot(0.0F);
                if (ticks <= 20 || !validDockCore(source.getServer(), station, ticket.targetPosition())) {
                    // Exact NTM DOCKING load wait: no movement until a real port is available.
                    capsule.setDeltaMovement(Vec3.ZERO);
                    break;
                }
                capsule.setPos(target.x, capsule.getY(), target.z);
                capsule.setFlightVelocity(0.1D);
                // NTM stores +0.1 as rocketVelocity, then EntityThrowableNT integrates it through
                // EntityRideableRocket.motionMult()==4.
                capsule.setDeltaMovement(0.0D, 0.1D, 0.0D);
                capsule.setPos(capsule.getX(),
                        capsule.getY() + 0.1D * NTM_MOTION_MULTIPLIER, capsule.getZ());
                if (capsule.getY() + CAPSULE_HEIGHT
                        > ticket.targetPosition().getY() + 1.5D) {
                    capsule.setPos(target);
                    capsule.setDeltaMovement(Vec3.ZERO);
                    capsule.setYRot(DOCK_YAW);
                    capsule.setXRot(0.0F);
                    capsule.setNoGravity(true);
                    if (!completeCoreDock(source.getServer(), station, ticket.targetPosition(),
                            capsule.getUUID())) {
                        recover(capsule);
                        break;
                    }
                    capsule.setDockLocked(true);
                    capsule.setCapsuleState(ReturnCapsuleState.STATION_DOCKED);
                    commit(capsule, ticket);
                }
            }
            case STATION_UNDOCKING -> {
                capsule.setNoGravity(true);
                capsule.setYRot(DOCK_YAW);
                capsule.setXRot(0.0F);
                capsule.setFlightVelocity(-0.1D);
                // Same NTM -0.1 velocity and fourfold inherited motion integration as docking.
                capsule.setDeltaMovement(0.0D, -0.1D, 0.0D);
                capsule.setPos(capsule.getX(),
                        capsule.getY() - 0.1D * NTM_MOTION_MULTIPLIER, capsule.getZ());
                // NTM's Y<32 boundary belonged to its fixed legacy orbit dimension. The modern
                // target scene is already preloaded, so preserve the visible -0.1 departure but
                // hand off after the same bounded 24-block corridor used by final approach.
                if (capsule.getY() <= stationUndockEnd(ticket.sourcePosition()).y) {
                    advance(capsule, ReturnCapsuleState.DEORBIT);
                }
            }
            case DEORBIT -> {
                capsule.setNoGravity(true);
                capsule.setDeltaMovement(Vec3.ZERO);
                transferToSurface(capsule, source, ticket);
            }
            case REENTRY -> {
                capsule.setNoGravity(true);
                LandingStep landing = tickNtmLanding(capsule, ticket);
                if (landing.tipped()) {
                    finishSurfaceTipping(capsule, landing.ticket(), landing.explosive());
                } else if (landing.impacted()) {
                    finishSurfaceLanding(capsule, landing.ticket());
                } else if (capsule.getY() <= landing.landed().y + 18.0D) {
                    advance(capsule, ReturnCapsuleState.SURFACE_LANDING);
                }
            }
            case SURFACE_LANDING -> {
                capsule.setNoGravity(true);
                LandingStep landing = tickNtmLanding(capsule, ticket);
                if (landing.tipped()) {
                    finishSurfaceTipping(capsule, landing.ticket(), landing.explosive());
                } else if (landing.impacted()) {
                    finishSurfaceLanding(capsule, landing.ticket());
                }
            }
            default -> { }
        }
        // LAUNCHING can cross a chunk boundary in this very tick. Updating only before the NTM
        // motion step leaves the destination chunk without an entity-ticking ticket until the next
        // tick, but an unobserved capsule may already have been unloaded by then. Hand the ticket
        // forward immediately after movement while the entity is still live. A dimension transfer
        // removes this source instance, so the recreated target entity owns its own ticket instead.
        if (!capsule.isRemoved() && capsule.level() == source
                && capsule.transitionTicket().isPresent()) {
            maintainFlightChunkTicket(capsule, source,
                    capsule.transitionTicket().orElseThrow());
        }
    }

    private static void transferToStation(ReusableReturnCapsuleEntity capsule, ServerLevel source,
                                          ReturnCapsuleTransitionTicket ticket) {
        ServerLevel orbit = source.getServer().getLevel(SpaceDimensions.ORBIT);
        StationRecord station = SpaceSavedData.get(source.getServer()).station(ticket.stationId()).orElse(null);
        if (orbit == null || station == null || !validDockCore(source.getServer(), station,
                ticket.targetPosition())
                || !ticket.bodyId().equals(station.currentBody())
                || !resolveSurfaceBinding(source.getServer(), station)
                .map(binding -> binding.dimension().equals(ticket.sourceDimension())).orElse(false)
                || !ticket.targetDimension().equals(SpaceDimensions.ORBIT.location())) {
            recover(capsule); return;
        }
        BlockPos dock = ticket.targetPosition();
        Vec3 target = orbitInsertionStart(stationDockedPosition(dock));
        if (!transferTargetReady(orbit, ticket, target)) return;
        if (!clientArmedForTransfer(capsule, ticket)) return;
        ReusableReturnCapsuleEntity moved = moveVehicleAndPassenger(capsule, orbit, target);
        if (moved == null) return;
        // Stay in NTM TRANSFER semantics until the target client proves that the same capsule is
        // tracked and the local passenger graph has been rebuilt. reconcileTransferBoundary then
        // enters insertion exactly once.
    }

    private static void transferToSurface(ReusableReturnCapsuleEntity capsule, ServerLevel source,
                                          ReturnCapsuleTransitionTicket ticket) {
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ticket.targetDimension());
        ServerLevel surface = source.getServer().getLevel(key);
        StationRecord station = SpaceSavedData.get(source.getServer()).station(ticket.stationId()).orElse(null);
        if (surface == null || station == null || !ticket.bodyId().equals(station.currentBody())
                || !resolveSurfaceBinding(source.getServer(), station)
                .map(binding -> binding.dimension().equals(ticket.targetDimension())).orElse(false)) {
            recover(capsule); return;
        }
        BlockPos landing = ticket.targetPosition();
        Vec3 target = surfaceEntryPosition(surface, ticket);
        if (!transferTargetReady(surface, ticket, target)) return;
        if (!clientArmedForTransfer(capsule, ticket)) return;
        ReusableReturnCapsuleEntity moved = moveVehicleAndPassenger(capsule, surface, target);
        if (moved == null) return;
    }

    /** Holds the source world invariant until the client thread has captured the transition. */
    private static boolean clientArmedForTransfer(ReusableReturnCapsuleEntity capsule,
                                                  ReturnCapsuleTransitionTicket ticket) {
        if (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.CLIENT_ARMED) return true;
        if (ticket.stage() != ReturnCapsuleTransitionTicket.Stage.PREPARED) return false;
        Entity passenger = capsule.getFirstPassenger();
        if (!(passenger instanceof ServerPlayer player)
                || !player.getUUID().equals(ticket.passengerId())
                || !player.serverLevel().dimension().location().equals(ticket.sourceDimension())) {
            recover(capsule);
            return false;
        }
        capsule.setNoGravity(true);
        capsule.setDeltaMovement(Vec3.ZERO);
        lockPassengerToCapsule(player, capsule);
        sendTransferHandshake(capsule, ticket, player, capsule.phaseTicks() <= 1);
        return false;
    }

    private static ReusableReturnCapsuleEntity moveVehicleAndPassenger(ReusableReturnCapsuleEntity capsule,
                                                                        ServerLevel destination, Vec3 target) {
        Entity passenger = capsule.getFirstPassenger();
        if (!(passenger instanceof ServerPlayer player)) { recover(capsule); return null; }
        ReturnCapsuleTransitionTicket ticket = capsule.transitionTicket().orElse(null);
        if (ticket == null) { recover(capsule); return null; }
        if (ticket.stage() != ReturnCapsuleTransitionTicket.Stage.CLIENT_ARMED) return null;
        acquireEndpointTicket(destination.getServer(), ticket.targetDimension(),
                ticket.targetPosition(), ticket);
        ServerLevel source = (ServerLevel) capsule.level();
        capsule.beginTransferDismount();
        player.stopRiding();
        capsule.endTransferDismount();

        /*
         * NTM 1.7.10 could transfer the player first and then recreate/mount the rocket before its
         * old tracker flushed. In 1.20.1 ServerPlayer.teleportTo immediately starts the Respawn and
         * target-chunk tracking sequence. Creating the vehicle only afterwards leaves a real race
         * in which AddEntity/SetPassengers is discarded and the client falls forever while both
         * sides wait at REMOUNT_PENDING.
         *
         * VS: Genesis' modern EntityTeleporter establishes the target vehicle first with
         * addDuringTeleport, then teleports and reattaches every passenger. Preserve NTM's logical
         * identity, NBT and final mounted contract, but use that proven high-version ordering so
         * the target player's initial tracker snapshot already contains the capsule.
         */
        Entity created = capsule.getType().create(destination);
        if (!(created instanceof ReusableReturnCapsuleEntity moved)) {
            releaseEndpointTicket(destination.getServer(), ticket.targetDimension(),
                    ticket.targetPosition(), ticket);
            player.startRiding(capsule, true);
            recover(capsule);
            return null;
        }
        moved.restoreFrom(capsule);
        moved.moveTo(target.x, target.y, target.z, capsule.getYRot(), capsule.getXRot());
        moved.setDeltaMovement(Vec3.ZERO);
        setTransitionStage(moved, ticket, ReturnCapsuleTransitionTicket.Stage.VEHICLE_TRANSFERRED,
                "target vehicle added before player Respawn");
        moved.setFlightTicketChunk(null);
        destination.addDuringTeleport(moved);
        if (!moved.getUUID().equals(capsule.getUUID())) {
            recover(moved);
            player.startRiding(capsule, true);
            return null;
        }
        capsule.flightTicketChunk().ifPresent(chunk -> source.getChunkSource().removeRegionTicket(
                TRANSFER_TICKET, new ChunkPos(chunk), FLIGHT_TICKET_RADIUS, ticket.ticketId()));
        capsule.setFlightTicketChunk(null);
        capsule.remove(Entity.RemovalReason.CHANGED_DIMENSION);

        player.teleportTo(destination, target.x, target.y + 1.15D, target.z,
                player.getYRot(), player.getXRot());
        if (player.serverLevel() != destination) {
            recover(moved);
            return null;
        }
        if (!player.startRiding(moved, true)) {
            recover(moved);
            return null;
        }
        setTransitionStage(moved, ticket, ReturnCapsuleTransitionTicket.Stage.REMOUNT_PENDING,
                "server player remounted target vehicle");
        lockPassengerToCapsule(player, moved);
        sendPassengerGraph(player, moved, true);
        moved.resetMissingPassengerTicks();
        releaseEndpointTicket(player.server, ticket.sourceDimension(), ticket.sourcePosition(), ticket);
        return moved;
    }

    /** Accepts source-world proof only while the exact player is still riding the exact source pod. */
    public static void confirmClientArmed(ServerPlayer player, UUID ticketId, UUID capsuleId) {
        if (!(player.getVehicle() instanceof ReusableReturnCapsuleEntity capsule)
                || !capsule.getUUID().equals(capsuleId)) return;
        ReturnCapsuleTransitionTicket ticket = capsule.transitionTicket().orElse(null);
        if (ticket == null || !ticket.ticketId().equals(ticketId)
                || !ticket.passengerId().equals(player.getUUID())
                || !player.serverLevel().dimension().location().equals(ticket.sourceDimension())
                || !atTransferBoundary(capsule, ticket.direction())) return;
        if (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.PREPARED) {
            setTransitionStage(capsule, ticket, ReturnCapsuleTransitionTicket.Stage.CLIENT_ARMED,
                    "source client captured departure frame");
        } else if (ticket.stage() != ReturnCapsuleTransitionTicket.Stage.CLIENT_ARMED) {
            return;
        }
        lockPassengerToCapsule(player, capsule);
    }

    private static boolean atTransferBoundary(ReusableReturnCapsuleEntity capsule,
                                              ReturnCapsuleTransitionTicket.Direction direction) {
        return direction == ReturnCapsuleTransitionTicket.Direction.TO_STATION
                ? capsule.capsuleState() == ReturnCapsuleState.ASCENT_TRANSITION
                : capsule.capsuleState() == ReturnCapsuleState.DEORBIT;
    }

    /** Accepts only proof for the active player/capsule/ticket graph in the target dimension. */
    public static void confirmClientTracking(ServerPlayer player, UUID ticketId, UUID capsuleId) {
        if (!(player.getVehicle() instanceof ReusableReturnCapsuleEntity capsule)
                || !capsule.getUUID().equals(capsuleId)) return;
        ReturnCapsuleTransitionTicket ticket = capsule.transitionTicket().orElse(null);
        if (ticket == null || !ticket.ticketId().equals(ticketId)
                || !ticket.passengerId().equals(player.getUUID())
                || !player.serverLevel().dimension().location().equals(ticket.targetDimension())) return;
        lockPassengerToCapsule(player, capsule);
        if (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.REMOUNT_PENDING) {
            setTransitionStage(capsule, ticket, ReturnCapsuleTransitionTicket.Stage.TRACKING_CONFIRMED,
                    "target client tracks and rides exact capsule UUID");
        } else if (ticket.stage() != ReturnCapsuleTransitionTicket.Stage.TRACKING_CONFIRMED
                && ticket.stage() != ReturnCapsuleTransitionTicket.Stage.TRANSFERRED) {
            return;
        }
        new ReturnCapsuleTrackingCommitPacket(ticket.ticketId(), capsule.getUUID(),
                ticket.targetDimension()).sendTo(player);
    }

    /** Releases the server barrier only after the client proves it received our tracking proof. */
    public static void confirmClientReady(ServerPlayer player, UUID ticketId, UUID capsuleId) {
        if (!(player.getVehicle() instanceof ReusableReturnCapsuleEntity capsule)
                || !capsule.getUUID().equals(capsuleId)) return;
        ReturnCapsuleTransitionTicket ticket = capsule.transitionTicket().orElse(null);
        if (ticket == null || !ticket.ticketId().equals(ticketId)
                || !ticket.passengerId().equals(player.getUUID())
                || !player.serverLevel().dimension().location().equals(ticket.targetDimension())) return;
        lockPassengerToCapsule(player, capsule);
        if (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.TRACKING_CONFIRMED) {
            setTransitionStage(capsule, ticket, ReturnCapsuleTransitionTicket.Stage.TRANSFERRED,
                    "target client rendered and accepted target scene");
        } else if (ticket.stage() != ReturnCapsuleTransitionTicket.Stage.TRANSFERRED) {
            return;
        }
        new ReturnCapsuleTransitionCompletePacket(ticket.ticketId(), capsule.getUUID(),
                ticket.targetDimension()).sendTo(player);
    }

    private static void sendTransferHandshake(ReusableReturnCapsuleEntity capsule,
                                              ReturnCapsuleTransitionTicket ticket,
                                              ServerPlayer player, boolean immediate) {
        if (!immediate && player.serverLevel().getGameTime() % 10L != 0L) return;
        // Pre-install the target station context before ServerPlayer.teleportTo queues Respawn.
        // The later dimension event sends the same snapshot again, but the orbit renderer can now
        // build the Genesis cube on its very first target frame instead of exposing black vacuum.
        if (ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_STATION) {
            SpaceSavedData.get(player.server).station(ticket.stationId()).ifPresent(station ->
                    new StationContextPacket(ObservationContext.from(station,
                            CelestialRegistryRuntime.current().generation())).sendTo(player));
        }
        new ReturnCapsuleTransitionPacket(ticket.ticketId(), capsule.getUUID(),
                ticket.targetDimension(),
                ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_STATION).sendTo(player);
    }

    private static void setTransitionStage(ReusableReturnCapsuleEntity capsule,
                                           ReturnCapsuleTransitionTicket ticket,
                                           ReturnCapsuleTransitionTicket.Stage stage,
                                           String reason) {
        ReturnCapsuleTransitionTicket updated = ticket.withStage(stage);
        capsule.setTransitionTicket(updated);
        logTransition(capsule, updated, reason);
    }

    private static void logTransition(ReusableReturnCapsuleEntity capsule,
                                      ReturnCapsuleTransitionTicket ticket,
                                      String reason) {
        LOGGER.info("[Wildfires return capsule/server] ticket={} capsule={} stage={} dimension={} "
                        + "direction={} reason={}", ticket.ticketId(), capsule.getUUID(), ticket.stage(),
                capsule.level().dimension().location(), ticket.direction(), reason);
    }

    private static void lockPassengerToCapsule(ServerPlayer player,
                                               ReusableReturnCapsuleEntity capsule) {
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        // positionRider owns the attitude-aware seat transform; forcing a separate Y offset here
        // would momentarily disagree with it and recreate the visible ejection frame.
        capsule.positionRider(player);
    }

    /**
     * Repairs the only crash window that can occur between Forge's entity move and the following
     * ticket/state writes. The destination dimension is durable evidence that the transfer happened.
     */
    private static boolean reconcileTransferBoundary(ReusableReturnCapsuleEntity capsule,
                                                     ServerLevel currentLevel,
                                                     ReturnCapsuleTransitionTicket ticket) {
        ResourceLocation current = currentLevel.dimension().location();
        if (!current.equals(ticket.sourceDimension()) && !current.equals(ticket.targetDimension())) {
            recover(capsule);
            return false;
        }
        Entity passenger = capsule.getFirstPassenger();
        if (ticket.hasKnownPassenger()
                && (!(passenger instanceof ServerPlayer player)
                || !player.getUUID().equals(ticket.passengerId()))) {
            // NTM keeps a thrower reference and remounts a transiently detached rider for up to
            // sixty ticks. Never teleport a missing rider here: a different dimension or vehicle
            // is evidence of a broken transaction and must stop, not start another transfer.
            ServerPlayer expected = findPlayer(currentLevel.getServer(), ticket.passengerId());
            if (expected == null && currentLevel.getServer().overworld().getGameTime()
                    - ticket.createdGameTime() <= TRANSFER_TICKET_TIMEOUT_TICKS) {
                capsule.setNoGravity(true);
                capsule.setDeltaMovement(Vec3.ZERO);
                return false;
            }
            if (expected != null && expected.serverLevel() == currentLevel
                    && expected.getVehicle() == null
                    && capsule.incrementMissingPassengerTicks() <= 60) {
                if (expected.startRiding(capsule, true)) {
                    capsule.resetMissingPassengerTicks();
                    if (current.equals(ticket.targetDimension())
                            && ticket.stage() == ReturnCapsuleTransitionTicket.Stage.REMOUNT_PENDING) {
                        sendTransferHandshake(capsule, ticket, expected, true);
                        sendPassengerGraph(expected, capsule, true);
                    }
                    return true;
                }
                return false;
            }
            recover(capsule);
            return false;
        }
        capsule.resetMissingPassengerTicks();
        if (passenger instanceof ServerPlayer player) {
            lockPassengerToCapsule(player, capsule);
            if (current.equals(ticket.targetDimension())
                    && ticket.stage() == ReturnCapsuleTransitionTicket.Stage.REMOUNT_PENDING) {
                sendTransferHandshake(capsule, ticket, player, false);
            }
            if (current.equals(ticket.targetDimension())
                    && (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.REMOUNT_PENDING
                    || ticket.stage() == ReturnCapsuleTransitionTicket.Stage.TRACKING_CONFIRMED)) {
                sendPassengerGraph(player, capsule, false);
            }
        }
        if (!current.equals(ticket.targetDimension())) {
            return true;
        }
        if (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.PLAYER_TRANSFERRED
                || ticket.stage() == ReturnCapsuleTransitionTicket.Stage.VEHICLE_TRANSFERRED
                || ticket.stage() == ReturnCapsuleTransitionTicket.Stage.REMOUNT_PENDING) {
            capsule.setNoGravity(true);
            capsule.setDeltaMovement(Vec3.ZERO);
            return false;
        }
        if (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.TRACKING_CONFIRMED) {
            capsule.setNoGravity(true);
            capsule.setDeltaMovement(Vec3.ZERO);
            return false;
        }
        if (ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_STATION
                && (capsule.capsuleState() == ReturnCapsuleState.ASCENT_TRANSITION
                || capsule.capsuleState() == ReturnCapsuleState.SURFACE_LAUNCHING)) {
            advance(capsule, ReturnCapsuleState.STATION_APPROACH);
        } else if (ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_SURFACE
                && (capsule.capsuleState() == ReturnCapsuleState.DEORBIT
                || capsule.capsuleState() == ReturnCapsuleState.STATION_UNDOCKING)) {
            advance(capsule, ReturnCapsuleState.REENTRY);
        }
        return true;
    }

    /** Replays the authoritative server passenger graph after the target entity becomes trackable. */
    private static void sendPassengerGraph(ServerPlayer player,
                                           ReusableReturnCapsuleEntity capsule,
                                           boolean immediate) {
        if (!immediate && player.serverLevel().getGameTime() % PASSENGER_GRAPH_RETRY_TICKS != 0L) return;
        player.connection.send(new ClientboundSetPassengersPacket(capsule));
    }

    private static void advance(ReusableReturnCapsuleEntity capsule, ReturnCapsuleState state) {
        capsule.setPhaseTicks(0);
        if (state != ReturnCapsuleState.SURFACE_LAUNCHING
                && state != ReturnCapsuleState.STATION_APPROACH
                && state != ReturnCapsuleState.STATION_UNDOCKING
                && state != ReturnCapsuleState.REENTRY
                && state != ReturnCapsuleState.SURFACE_LANDING) {
            capsule.setFlightVelocity(0.0D);
        }
        capsule.setCapsuleState(state);
    }

    /** Direct Forge port of NTM reusable LAUNCHING acceleration and attitude integration. */
    private static void tickNtmLaunch(ReusableReturnCapsuleEntity capsule, int ticks) {
        double velocity = capsule.flightVelocity();
        if (velocity < 4.0D) {
            velocity += Mth.clamp(ticks / 120.0D * 0.05D, 0.0D, 0.05D);
            velocity = Math.min(4.0D, velocity);
        }
        float pitch = Mth.clamp((ticks - 60) * 0.3F, 0.0F, 45.0F);
        capsule.setFlightVelocity(velocity);
        capsule.setXRot(pitch);
        capsule.setYRot(DOCK_YAW);
        double pitchRadians = Math.toRadians(pitch - 90.0F);
        double yawRadians = Math.toRadians(180.0F - DOCK_YAW);
        Vec3 motion = new Vec3(-Math.sin(yawRadians) * Math.cos(pitchRadians) * velocity,
                -Math.sin(pitchRadians) * velocity,
                Math.cos(yawRadians) * Math.cos(pitchRadians) * velocity);
        capsule.setDeltaMovement(motion);
        capsule.setPos(capsule.position().add(motion.scale(NTM_MOTION_MULTIPLIER)));
    }

    /** Direct Forge port of reusable LANDING's terrain-distance velocity clamp. */
    private static LandingStep tickNtmLanding(ReusableReturnCapsuleEntity capsule,
                                              ReturnCapsuleTransitionTicket originalTicket) {
        ReturnCapsuleTransitionTicket ticket = avoidOccupiedLandingColumn(capsule, originalTicket);
        Vec3 landed = surfaceLandedPosition(ticket);
        // NTM asks World#getHeightValue every landing tick rather than trusting the height saved
        // when the trip was prepared. Preserve that live-terrain contract after the dimension move.
        int targetHeight = capsule.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(landed.x), Mth.floor(landed.z));
        // Modern swept AABB collision can leave a sub-block numerical gap above modded terrain.
        // NTM's onImpact boundary is the surface itself, not an extra multi-second hover at its
        // -0.005 terminal clamp. Resolve only that final collision epsilon to the live height.
        if (capsule.getY() - targetHeight <= 0.25D) {
            Vec3 impact = new Vec3(landed.x, targetHeight, landed.z);
            capsule.setPos(impact);
            capsule.setDeltaMovement(Vec3.ZERO);
            capsule.setFlightVelocity(0.0D);
            return new LandingStep(ticket, impact, true, false, false);
        }
        double velocity = Mth.clamp((targetHeight - capsule.getY()) * 0.01D, -1.0D, -0.005D);
        capsule.setFlightVelocity(velocity);
        capsule.setXRot(0.0F);
        capsule.setPos(landed.x, capsule.getY(), landed.z);
        capsule.setDeltaMovement(0.0D, velocity, 0.0D);
        capsule.move(net.minecraft.world.entity.MoverType.SELF,
                capsule.getDeltaMovement().scale(NTM_MOTION_MULTIPLIER));
        var fluid = capsule.level().getFluidState(capsule.blockPosition());
        boolean tipped = !fluid.isEmpty();
        return new LandingStep(ticket, landed, capsule.verticalCollision || capsule.onGround(),
                tipped, tipped && fluid.is(FluidTags.LAVA));
    }

    /** Exact reusable LANDING vertical-column conflict rule: one random axis, exactly +/-5. */
    private static ReturnCapsuleTransitionTicket avoidOccupiedLandingColumn(
            ReusableReturnCapsuleEntity capsule, ReturnCapsuleTransitionTicket ticket) {
        if (ticket.direction() != ReturnCapsuleTransitionTicket.Direction.TO_SURFACE
                || !(capsule.level() instanceof ServerLevel surface)) return ticket;
        Vec3 landed = surfaceLandedPosition(ticket);
        int targetHeight = surface.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(landed.x), Mth.floor(landed.z));
        AABB bounds = capsule.getBoundingBox();
        AABB descentColumn = new AABB(bounds.minX, targetHeight, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ);
        if (surface.getEntitiesOfClass(ReusableReturnCapsuleEntity.class, descentColumn,
                other -> other != capsule && !other.isRemoved()).isEmpty()) return ticket;

        int distance = surface.random.nextBoolean() ? -5 : 5;
        boolean shiftX = surface.random.nextBoolean();
        BlockPos old = ticket.surfacePosition();
        int x = old.getX() + (shiftX ? distance : 0);
        int z = old.getZ() + (shiftX ? 0 : distance);
        int y = surface.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos shifted = new BlockPos(x, y, z);
        ReturnCapsuleTransitionTicket updated = ticket.withSurfacePosition(shifted);
        releaseEndpointTicket(surface.getServer(), ticket.surfaceDimension(), old, ticket);
        acquireEndpointTicket(surface.getServer(), updated.surfaceDimension(), shifted, updated);
        capsule.setHomeSurface(updated.surfaceDimension(), shifted);
        capsule.setTransitionTicket(updated);
        return updated;
    }

    /** NTM onImpact: only a real terrain collision may turn LANDING into LANDED. */
    private static void finishSurfaceLanding(ReusableReturnCapsuleEntity capsule,
                                             ReturnCapsuleTransitionTicket ticket) {
        capsule.setDeltaMovement(Vec3.ZERO);
        capsule.setFlightVelocity(0.0D);
        capsule.setXRot(0.0F);
        capsule.setNoGravity(false);
        // NTM onImpact always enters LANDED. The retained internal drive does not skip the visible
        // 45-tick door opening; it must be explicitly re-activated before another launch.
        capsule.setCapsuleState(ReturnCapsuleState.SURFACE_LANDED);
        commit(capsule, ticket);
    }

    /** NTM liquid contact commits the arrival but replaces LANDED with a stationary tip-over. */
    private static void finishSurfaceTipping(ReusableReturnCapsuleEntity capsule,
                                             ReturnCapsuleTransitionTicket ticket,
                                             boolean explosive) {
        capsule.setDeltaMovement(Vec3.ZERO);
        capsule.setFlightVelocity(0.0D);
        capsule.setNoGravity(true);
        capsule.beginTipping(explosive);
        commit(capsule, ticket);
    }

    /** Server-side terminal behavior for NTM's lava-only willExplode flag. */
    static boolean tickTipping(ReusableReturnCapsuleEntity capsule) {
        capsule.setNoGravity(true);
        capsule.setDeltaMovement(Vec3.ZERO);
        if (!capsule.tippingExplosive() || capsule.phaseTicks() < TIPPING_EXPLOSION_TICKS
                || !(capsule.level() instanceof ServerLevel level)) return false;
        UUID stationId = capsule.stationId().orElse(null);
        if (stationId != null) {
            StationService.setReturnCapsule(SpaceSavedData.get(level.getServer()), stationId,
                    capsule.getUUID(), false, level.getServer().overworld().getGameTime());
        }
        ItemStack recovered = new ItemStack(SpaceContentRegister.REUSABLE_RETURN_CAPSULE_ITEM.get());
        recovered.getOrCreateTag().put("wildfires_capsule_fuel", capsule.saveFuelForItem());
        ItemStack navigationTape = capsule.navigationTape();
        double x = capsule.getX();
        double y = capsule.getY();
        double z = capsule.getZ();
        capsule.spawnAtLocation(recovered, 0.0F);
        if (!navigationTape.isEmpty()) capsule.spawnAtLocation(navigationTape, 0.0F);
        capsule.discard();
        level.explode(null, x, y, z, 5.0F, Level.ExplosionInteraction.TNT);
        return true;
    }

    private record LandingStep(ReturnCapsuleTransitionTicket ticket, Vec3 landed,
                               boolean impacted, boolean tipped, boolean explosive) {
    }

    private static Vec3 orbitInsertionStart(Vec3 dock) {
        // NTM enters the old dedicated orbit world at Y=0 and then spends roughly a minute
        // climbing to its Y=127 station. The modern transfer barrier has already loaded and
        // rendered this station scene before it releases the player, so repeating that hidden
        // 124-block traversal only creates dead time. Keep NTM's twenty-tick port wait and exact
        // +0.1 motion, but begin the visible final approach a bounded 24 blocks below the port.
        return orbitApproachStart(dock);
    }

    private static Vec3 orbitApproachStart(Vec3 dock) {
        return dock.add(0.0D, -24.0D, 0.0D);
    }

    private static Vec3 orbitUndockEnd(Vec3 dock) {
        return dock.add(0.0D, -24.0D, 0.0D);
    }

    private static Vec3 surfaceLandedPosition(ReturnCapsuleTransitionTicket ticket) {
        BlockPos landing = ticket.surfacePosition();
        // The saved endpoint is the capsule's base block position, not the supporting terrain.
        return new Vec3(landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D);
    }

    /**
     * NTM {@code TileEntityOrbitalStation.spawnRocket} and the final DOCKING branch both use
     * {@code coreY + 1.5 - rocket.height}. The core owns the entity at that anchor; it is not
     * pushed into place by proxy-block collision.
     */
    public static Vec3 stationDockedPosition(BlockPos corePosition) {
        return new Vec3(corePosition.getX() + 0.5D,
                corePosition.getY() + 1.5D - CAPSULE_HEIGHT, corePosition.getZ() + 0.5D);
    }

    /** The safe foot position is the exposed top face of NTM's top-centre proxy. */
    public static Vec3 stationDismountPosition(BlockPos corePosition) {
        return new Vec3(corePosition.getX() + 0.5D, corePosition.getY() + 2.0D,
                corePosition.getZ() + 0.5D);
    }

    /** Resolves the real bidirectionally locked port rather than guessing from entity coordinates. */
    public static Optional<BlockPos> dockedCorePosition(ReusableReturnCapsuleEntity capsule) {
        if (!(capsule.level() instanceof ServerLevel level)
                || capsule.capsuleState() != ReturnCapsuleState.STATION_DOCKED
                || !capsule.dockLocked()) return Optional.empty();
        UUID stationId = capsule.stationId().orElse(null);
        if (stationId == null) return Optional.empty();
        StationRecord station = SpaceSavedData.get(level.getServer()).station(stationId).orElse(null);
        if (station == null) return Optional.empty();
        return dockForCapsule(level.getServer(), station, capsule.getUUID())
                .filter(dock -> capsule.position().distanceToSqr(
                        stationDockedPosition(dock.position())) < 0.0625D)
                .map(StationDockRecord::position);
    }

    public static Vec3 stationInsertionStart(BlockPos corePosition) {
        return orbitInsertionStart(stationDockedPosition(corePosition));
    }

    public static Vec3 stationApproachStart(BlockPos corePosition) {
        return orbitApproachStart(stationDockedPosition(corePosition));
    }

    public static Vec3 stationUndockEnd(BlockPos corePosition) {
        return orbitUndockEnd(stationDockedPosition(corePosition));
    }

    private static Vec3 surfaceEntryPosition(ServerLevel surface, ReturnCapsuleTransitionTicket ticket) {
        Vec3 landed = surfaceLandedPosition(ticket);
        // NTM's Y=800 is an Earth-specific absolute coordinate. Preserve its corridor shape while
        // anchoring it to the actual bound surface and scaling it for body size/atmosphere.
        double altitude = transferProfile(surface.getServer(), ticket.bodyId()).reentryAltitude();
        return new Vec3(landed.x, landed.y + altitude, landed.z);
    }

    private static CelestialTransferProfile transferProfile(MinecraftServer server,
                                                             ResourceLocation bodyId) {
        CelestialDefinition definition = CelestialDefinitionRegistry.get(server.registryAccess()).get(bodyId);
        if (definition == null) {
            throw new IllegalStateException("Missing celestial definition for transfer body " + bodyId);
        }
        return CelestialTransferProfile.resolve(bodyId, definition.visual(),
                CelestialConfig.serverSettings().planetSettings());
    }

    private static Vec3 surfaceLandingStart(Vec3 landed) {
        return landed.add(0.0D, 18.0D, 0.0D);
    }

    private static double phase(int ticks, int duration) {
        double linear = Math.max(0.0D, Math.min(1.0D, ticks / (double) duration));
        return linear * linear * (3.0D - 2.0D * linear);
    }

    private static void placeAlong(ReusableReturnCapsuleEntity capsule, Vec3 start, Vec3 end,
                                   double progress) {
        Vec3 next = start.lerp(end, progress);
        Vec3 velocity = next.subtract(capsule.position());
        capsule.setPos(next);
        capsule.setDeltaMovement(velocity);
    }

    /** Deterministic administrator recovery: source rolls back; destination commits. */
    public static ActionResult recoverTransaction(ReusableReturnCapsuleEntity capsule) {
        ReturnCapsuleTransitionTicket ticket = capsule.transitionTicket().orElse(null);
        if (!(capsule.level() instanceof ServerLevel level) || ticket == null) {
            return ActionResult.RECOVERY_REQUIRED;
        }
        ResourceLocation current = level.dimension().location();
        if (current.equals(ticket.targetDimension())) {
            StationRecord station = SpaceSavedData.get(level.getServer()).station(ticket.stationId()).orElse(null);
            if (station == null || !ticket.bodyId().equals(station.currentBody())
                    || !resolveSurfaceBinding(level.getServer(), station)
                    .map(binding -> binding.dimension().equals(ticket.surfaceDimension())).orElse(false)) {
                return ActionResult.NO_SURFACE;
            }
            if (ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_STATION) {
                if (!validDockCore(level.getServer(), station, ticket.targetPosition())) return ActionResult.NO_CORE;
                if (!completeCoreDock(level.getServer(), station, ticket.targetPosition(), capsule.getUUID())) {
                    return ActionResult.NOT_DOCKED;
                }
                Vec3 dock = stationDockedPosition(ticket.targetPosition());
                capsule.setPos(dock);
                capsule.setYRot(DOCK_YAW);
                capsule.setXRot(0.0F);
                capsule.setDockLocked(true);
                capsule.setCapsuleState(ReturnCapsuleState.STATION_DOCKED);
            } else {
                capsule.setPos(surfaceLandedPosition(ticket));
                capsule.setNoGravity(false);
                capsule.setDockLocked(false);
                capsule.setCapsuleState(ReturnCapsuleState.SURFACE_LANDED);
            }
            abortClientTransition(level.getServer(), ticket, capsule.getUUID());
            commit(capsule, ticket);
            return ActionResult.RECOVERED;
        }
        if (current.equals(ticket.sourceDimension()) && capsule.rollbackFuelTrip(ticket.ticketId())) {
            if (ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_STATION) {
                StationRecord station = SpaceSavedData.get(level.getServer()).station(ticket.stationId())
                        .orElse(null);
                if (station != null) releaseCoreDockLock(level.getServer(), station,
                        ticket.targetPosition(), capsule.getUUID());
                capsule.setPos(surfaceLandedPosition(ticket));
                capsule.setNoGravity(false);
                capsule.setDockLocked(false);
                capsule.setCapsuleState(ReturnCapsuleState.SURFACE_LANDED);
            } else {
                StationRecord station = SpaceSavedData.get(level.getServer()).station(ticket.stationId()).orElse(null);
                if (station == null || !validDockCore(level.getServer(), station,
                        ticket.sourcePosition())) return ActionResult.NO_CORE;
                if (!completeCoreDock(level.getServer(), station, ticket.sourcePosition(),
                        capsule.getUUID())) return ActionResult.NO_DOCK;
                capsule.setPos(stationDockedPosition(ticket.sourcePosition()));
                capsule.setNoGravity(true);
                capsule.setDockLocked(true);
                capsule.setCapsuleState(ReturnCapsuleState.STATION_DOCKED);
            }
            capsule.clearTransitionTicket();
            capsule.setPhaseTicks(0);
            releaseTripTickets(level.getServer(), ticket);
            abortClientTransition(level.getServer(), ticket, capsule.getUUID());
            return ActionResult.RECOVERED;
        }
        return ActionResult.RECOVERY_REQUIRED;
    }

    private static void commit(ReusableReturnCapsuleEntity capsule, ReturnCapsuleTransitionTicket ticket) {
        if (capsule.commitFuelTrip(ticket.ticketId())) {
            // A released key is ready for the next NTM AWAITING edge. A key still held across the
            // dimension boundary stays disarmed so it cannot manufacture an automatic return.
            if (capsule.primaryActionPressed()) capsule.disarmPrimaryAction();
            else capsule.armPrimaryAction();
            if (capsule.level() instanceof ServerLevel level) {
                capsule.flightTicketChunk().ifPresent(chunk -> level.getChunkSource().removeRegionTicket(
                        TRANSFER_TICKET, new ChunkPos(chunk), FLIGHT_TICKET_RADIUS, ticket.ticketId()));
                capsule.setFlightTicketChunk(null);
                releaseTripTickets(level.getServer(), ticket);
            }
            // Keep a persisted COMMITTED ticket for at least one entity tick. The tank also keeps
            // the ticket UUID as a tombstone, so replay after a crash cannot drain a second bucket.
            setTransitionStage(capsule, ticket, ReturnCapsuleTransitionTicket.Stage.COMMITTED,
                    "flight committed exactly once");
            capsule.setPhaseTicks(0);
        } else {
            recover(capsule);
        }
    }

    private static void recover(ReusableReturnCapsuleEntity capsule) {
        if (capsule.capsuleState() == ReturnCapsuleState.RECOVERY_REQUIRED
                && capsule.transitionTicket().filter(ticket ->
                ticket.stage() == ReturnCapsuleTransitionTicket.Stage.RECOVERY).isPresent()) {
            capsule.setNoGravity(true);
            capsule.setDeltaMovement(Vec3.ZERO);
            return;
        }
        capsule.transitionTicket().ifPresent(ticket -> {
            if (capsule.level() instanceof ServerLevel level) {
                abortClientTransition(level.getServer(), ticket, capsule.getUUID());
                capsule.flightTicketChunk().ifPresent(chunk -> level.getChunkSource().removeRegionTicket(
                        TRANSFER_TICKET, new ChunkPos(chunk), FLIGHT_TICKET_RADIUS, ticket.ticketId()));
                capsule.setFlightTicketChunk(null);
                if (ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_STATION) {
                    SpaceSavedData.get(level.getServer()).station(ticket.stationId()).ifPresent(station ->
                            releaseCoreDockLock(level.getServer(), station, ticket.targetPosition(),
                                    capsule.getUUID()));
                }
            }
        });
        capsule.setCapsuleState(ReturnCapsuleState.RECOVERY_REQUIRED);
        capsule.setNoGravity(true);
        capsule.setDeltaMovement(Vec3.ZERO);
        capsule.transitionTicket().ifPresent(ticket -> setTransitionStage(capsule, ticket,
                ReturnCapsuleTransitionTicket.Stage.RECOVERY, "terminal recovery fuse"));
    }

    private static void abortClientTransition(MinecraftServer server,
                                              ReturnCapsuleTransitionTicket ticket,
                                              UUID capsuleId) {
        ServerPlayer player = findPlayer(server, ticket.passengerId());
        if (player != null) {
            new ReturnCapsuleTransitionAbortPacket(ticket.ticketId(), capsuleId).sendTo(player);
        }
    }

    /**
     * Keeps every chunk crossed by the short insertion/re-entry trajectory entity-ticking even
     * when the pilot disconnects or is a Forge FakePlayer. The bounded ticket is centred on the
     * durable target endpoint, covers all first-release capsule paths, and also expires after 600
     * ticks if a crash prevents the normal commit/release boundary.
     */
    private static void acquireTripTickets(MinecraftServer server,
                                           ReturnCapsuleTransitionTicket ticket) {
        acquireEndpointTicket(server, ticket.sourceDimension(), ticket.sourcePosition(), ticket);
        acquireEndpointTicket(server, ticket.targetDimension(), ticket.targetPosition(), ticket);
    }

    /** Keeps exactly one small ticking ticket under NTM's pitched, cross-chunk ascent. */
    private static void maintainFlightChunkTicket(ReusableReturnCapsuleEntity capsule,
                                                  ServerLevel level,
                                                  ReturnCapsuleTransitionTicket ticket) {
        long current = capsule.chunkPosition().toLong();
        Long previous = capsule.flightTicketChunk().orElse(null);
        if (previous != null && previous.longValue() == current) return;
        ChunkPos currentPos = new ChunkPos(current);
        level.getChunkSource().addRegionTicket(TRANSFER_TICKET, currentPos,
                FLIGHT_TICKET_RADIUS, ticket.ticketId());
        level.getChunkAt(capsule.blockPosition());
        if (previous != null) {
            level.getChunkSource().removeRegionTicket(TRANSFER_TICKET, new ChunkPos(previous),
                    FLIGHT_TICKET_RADIUS, ticket.ticketId());
        }
        capsule.setFlightTicketChunk(current);
    }

    private static void acquireEndpointTicket(MinecraftServer server, ResourceLocation dimension,
                                              BlockPos position, ReturnCapsuleTransitionTicket ticket) {
        ServerLevel level = level(server, dimension);
        if (level == null) return;
        level.getChunkSource().addRegionTicket(TRANSFER_TICKET, new ChunkPos(position),
                TRANSFER_TICKET_RADIUS, ticket.ticketId());
        level.getChunkAt(position);
    }

    private static boolean transferTargetReady(ServerLevel target,
                                               ReturnCapsuleTransitionTicket ticket,
                                               Vec3 entryPosition) {
        acquireEndpointTicket(target.getServer(), ticket.targetDimension(), ticket.targetPosition(), ticket);
        ChunkPos entryChunk = new ChunkPos(BlockPos.containing(entryPosition));
        return target.getChunkSource().isPositionTicking(entryChunk.toLong());
    }

    private static void releaseTripTickets(MinecraftServer server,
                                           ReturnCapsuleTransitionTicket ticket) {
        releaseEndpointTicket(server, ticket.sourceDimension(), ticket.sourcePosition(), ticket);
        releaseEndpointTicket(server, ticket.targetDimension(), ticket.targetPosition(), ticket);
    }

    private static void releaseEndpointTicket(MinecraftServer server, ResourceLocation dimension,
                                              BlockPos position, ReturnCapsuleTransitionTicket ticket) {
        ServerLevel level = level(server, dimension);
        if (level == null) return;
        level.getChunkSource().removeRegionTicket(TRANSFER_TICKET, new ChunkPos(position),
                TRANSFER_TICKET_RADIUS, ticket.ticketId());
    }

    private static ServerLevel level(MinecraftServer server, ResourceLocation dimension) {
        return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
    }

    private static Optional<SurfaceBinding> resolveSurfaceBinding(MinecraftServer server,
                                                                   StationRecord station) {
        return CelestialSurfaceBindingResolver.resolve(server, station.currentBody())
                .map(binding -> new SurfaceBinding(binding.dimension(), binding.level()));
    }

    /** Deterministic, bounded first-descent target based on the bound dimension's own spawn. */
    private static Optional<BlockPos> findSafeLanding(ServerLevel surface) {
        BlockPos spawn = surface.getSharedSpawnPos();
        for (int radius = 0; radius <= SAFE_LANDING_SEARCH_RADIUS; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (radius != 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    int x = spawn.getX() + dx;
                    int z = spawn.getZ() + dz;
                    int y = surface.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (isSafeLanding(surface, candidate)) return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isSafeLanding(ServerLevel surface, BlockPos base) {
        if (base.getY() < surface.getMinBuildHeight()
                || base.getY() + Math.ceil(CAPSULE_HEIGHT) >= surface.getMaxBuildHeight()
                || !surface.getWorldBorder().isWithinBounds(base)
                || !surface.getBlockState(base.below()).isFaceSturdy(surface, base.below(), Direction.UP)
                || !surface.getFluidState(base.below()).isEmpty()) {
            return false;
        }
        Vec3 centre = new Vec3(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        return surface.noCollision(capsuleBoundsAt(centre));
    }

    public static AABB capsuleBoundsAt(Vec3 base) {
        return new AABB(base.x - CAPSULE_HALF_WIDTH, base.y, base.z - CAPSULE_HALF_WIDTH,
                base.x + CAPSULE_HALF_WIDTH, base.y + CAPSULE_HEIGHT,
                base.z + CAPSULE_HALF_WIDTH);
    }

    private static boolean validCore(MinecraftServer server, StationRecord station) {
        return validDockCore(server, station, station.primaryDock().position());
    }

    private static boolean validDockCore(MinecraftServer server, StationRecord station, BlockPos position) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        if (orbit == null) return false;
        StationDockRecord dock = station.docks().values().stream()
                .filter(value -> value.position().equals(position)).findFirst().orElse(null);
        return orbit.getBlockState(position).is(SpaceContentRegister.STATION_CORE.get())
                && orbit.getBlockEntity(position) instanceof StationCoreBlockEntity core
                && core.stationId().filter(station.stationId()::equals).isPresent()
                && dock != null && core.dockId().filter(dock.id()::equals).isPresent()
                && StationCoreService.isComplete(orbit, position);
    }

    private static boolean reserveCoreDock(MinecraftServer server, StationRecord station,
                                           BlockPos position, UUID capsuleId) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        return orbit != null
                && validDockCore(server, station, position)
                && orbit.getBlockEntity(position) instanceof StationCoreBlockEntity core
                && core.stationId().filter(station.stationId()::equals).isPresent()
                && core.reserveDock(capsuleId);
    }

    private static boolean completeCoreDock(MinecraftServer server, StationRecord station,
                                            BlockPos position, UUID capsuleId) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        return orbit != null
                && validDockCore(server, station, position)
                && orbit.getBlockEntity(position) instanceof StationCoreBlockEntity core
                && core.stationId().filter(station.stationId()::equals).isPresent()
                && core.completeDock(capsuleId);
    }

    private static boolean releaseCoreDockLock(MinecraftServer server, StationRecord station,
                                               BlockPos position, UUID capsuleId) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        return orbit != null
                && orbit.getBlockEntity(position) instanceof StationCoreBlockEntity core
                && core.stationId().filter(station.stationId()::equals).isPresent()
                && core.releaseDockLock(capsuleId);
    }

    private static Optional<StationDockRecord> availableDock(MinecraftServer server,
                                                             StationRecord station, UUID capsuleId) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        if (orbit == null) return Optional.empty();
        return station.docks().values().stream()
                .sorted(Comparator.comparing(value -> value.id().toString()))
                .filter(dock -> validDockCore(server, station, dock.position()))
                .filter(dock -> orbit.getBlockEntity(dock.position()) instanceof StationCoreBlockEntity core
                        && core.claimedCapsuleId().map(capsuleId::equals).orElse(true))
                .findFirst();
    }

    private static Optional<StationDockRecord> dockForCapsule(MinecraftServer server,
                                                              StationRecord station, UUID capsuleId) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        if (orbit == null) return Optional.empty();
        return station.docks().values().stream()
                .filter(dock -> validDockCore(server, station, dock.position()))
                .filter(dock -> orbit.getBlockEntity(dock.position()) instanceof StationCoreBlockEntity core
                        && core.dockedCapsuleId().filter(capsuleId::equals).isPresent())
                .findFirst();
    }

    /** Finds the port UUID reservation even while its proxy shell is incomplete. */
    private static Optional<StationDockRecord> reservedDockForCapsule(MinecraftServer server,
                                                                      StationRecord station,
                                                                      UUID capsuleId) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        if (orbit == null) return Optional.empty();
        return station.docks().values().stream()
                .filter(dock -> orbit.getBlockState(dock.position())
                        .is(SpaceContentRegister.STATION_CORE.get()))
                .filter(dock -> orbit.getBlockEntity(dock.position()) instanceof StationCoreBlockEntity core
                        && core.stationId().filter(station.stationId()::equals).isPresent()
                        && core.dockId().filter(dock.id()::equals).isPresent()
                        && core.dockedCapsuleId().filter(capsuleId::equals).isPresent())
                .findFirst();
    }

    public static Optional<ReusableReturnCapsuleEntity> findDockedCapsule(MinecraftServer server,
                                                                          StationRecord station) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        if (orbit == null) return Optional.empty();
        return station.ownedReturnCapsules().stream().map(orbit::getEntity)
                .filter(ReusableReturnCapsuleEntity.class::isInstance)
                .map(ReusableReturnCapsuleEntity.class::cast)
                .filter(capsule -> capsule.stationId().filter(station.stationId()::equals).isPresent())
                .filter(capsule -> capsule.capsuleState() == ReturnCapsuleState.STATION_DOCKED)
                .filter(ReusableReturnCapsuleEntity::dockLocked)
                .filter(capsule -> dockForCapsule(server, station, capsule.getUUID())
                        .filter(dock -> capsule.position().distanceToSqr(
                                stationDockedPosition(dock.position())) < 0.0625D).isPresent())
                .filter(capsule -> Math.abs(net.minecraft.util.Mth.wrapDegrees(
                        capsule.getYRot() - DOCK_YAW)) < 0.5F && Math.abs(capsule.getXRot()) < 0.5F)
                .findFirst();
    }

    public static boolean allDocked(MinecraftServer server, StationRecord station) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        if (orbit == null) return station.ownedReturnCapsules().isEmpty();
        return station.ownedReturnCapsules().stream().allMatch(id -> {
            Entity entity = orbit.getEntity(id);
            return entity instanceof ReusableReturnCapsuleEntity capsule
                    && capsule.stationId().filter(station.stationId()::equals).isPresent()
                    && capsule.capsuleState() == ReturnCapsuleState.STATION_DOCKED
                    && capsule.dockLocked()
                    && dockForCapsule(server, station, capsule.getUUID())
                    .filter(dock -> capsule.position().distanceToSqr(
                            stationDockedPosition(dock.position())) < 0.0625D).isPresent()
                    && Math.abs(net.minecraft.util.Mth.wrapDegrees(capsule.getYRot() - DOCK_YAW)) < 0.5F
                    && Math.abs(capsule.getXRot()) < 0.5F;
        });
    }

    /** Repairs the bidirectional core/capsule lock after chunk load or a server restart. */
    public static void reconcileDockLocks(MinecraftServer server, StationRecord station) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        if (orbit == null) return;
        for (StationDockRecord dock : station.docks().values()) {
            if (!orbit.hasChunkAt(dock.position())
                    || !(orbit.getBlockEntity(dock.position()) instanceof StationCoreBlockEntity core)) continue;
            UUID locked = core.claimedCapsuleId().orElse(null);
            if (locked != null) {
                ReusableReturnCapsuleEntity capsule = findCapsule(server, locked).orElse(null);
                boolean trueDock = core.dockedCapsuleId().filter(locked::equals).isPresent();
                boolean valid = station.ownedReturnCapsules().contains(locked) && capsule != null
                        && capsule.stationId().filter(station.stationId()::equals).isPresent()
                        && (!trueDock && capsule.transitionTicket().filter(ticket ->
                                ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_STATION
                                        && ticket.targetPosition().equals(dock.position())
                                        && ownsTargetDockReservation(ticket.stage())).isPresent()
                        || trueDock && capsule.level() == orbit && capsule.dockLocked()
                        && capsule.capsuleState() == ReturnCapsuleState.STATION_DOCKED
                        && capsule.position().distanceToSqr(stationDockedPosition(dock.position())) < 0.0625D);
                if (!valid) core.releaseDockLock(locked);
            }
        }
        for (UUID capsuleId : station.ownedReturnCapsules()) {
            Entity entity = orbit.getEntity(capsuleId);
            if (!(entity instanceof ReusableReturnCapsuleEntity capsule) || !capsule.dockLocked()
                    || capsule.capsuleState() != ReturnCapsuleState.STATION_DOCKED) continue;
            station.docks().values().stream()
                    .filter(dock -> capsule.position().distanceToSqr(stationDockedPosition(dock.position())) < 0.0625D)
                    .findFirst().ifPresent(dock -> {
                        if (orbit.getBlockEntity(dock.position()) instanceof StationCoreBlockEntity core
                                && core.claimedCapsuleId().map(capsuleId::equals).orElse(true)) {
                            core.completeDock(capsuleId);
                        } else {
                            capsule.setDockLocked(false);
                            capsule.setCapsuleState(ReturnCapsuleState.RECOVERY_REQUIRED);
                        }
                    });
        }
    }

    private static boolean ownsTargetDockReservation(ReturnCapsuleTransitionTicket.Stage stage) {
        return stage == ReturnCapsuleTransitionTicket.Stage.PREPARED
                || stage == ReturnCapsuleTransitionTicket.Stage.CLIENT_ARMED
                || stage == ReturnCapsuleTransitionTicket.Stage.PLAYER_TRANSFERRED
                || stage == ReturnCapsuleTransitionTicket.Stage.VEHICLE_TRANSFERRED
                || stage == ReturnCapsuleTransitionTicket.Stage.REMOUNT_PENDING
                || stage == ReturnCapsuleTransitionTicket.Stage.TRACKING_CONFIRMED
                || stage == ReturnCapsuleTransitionTicket.Stage.TRANSFERRED;
    }

    private static Optional<ReusableReturnCapsuleEntity> findCapsule(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof ReusableReturnCapsuleEntity capsule) return Optional.of(capsule);
        }
        return Optional.empty();
    }

    /** Includes GameTest FakePlayers and modded server players not exposed by PlayerList. */
    private static ServerPlayer findPlayer(MinecraftServer server, UUID id) {
        ServerPlayer listed = server.getPlayerList().getPlayer(id);
        if (listed != null) return listed;
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player.getUUID().equals(id)) return player;
            }
        }
        return null;
    }

    public static double phaseProgress(ReusableReturnCapsuleEntity capsule, float partialTick) {
        int duration = switch (capsule.capsuleState()) {
            case SURFACE_CLOSING -> DOOR_CLOSE_TICKS;
            case SURFACE_LAUNCHING -> LAUNCH_TICKS;
            case ORBIT_INSERTION -> INSERTION_TICKS;
            case STATION_APPROACH -> APPROACH_TICKS;
            case STATION_UNDOCKING -> UNDOCK_TICKS;
            case REENTRY -> REENTRY_TICKS;
            case SURFACE_LANDING -> LANDING_TICKS;
            case SURFACE_TIPPING -> 100;
            default -> 1;
        };
        return Math.max(0.0D, Math.min(1.0D,
                (capsule.phaseTicks() + partialTick) / duration));
    }

    public enum ActionResult {
        STARTED(true, "space.wildfires.return_capsule.action.started"),
        RECOVERED(true, "space.wildfires.return_capsule.action.recovered"),
        NOT_RIDING(false, "space.wildfires.return_capsule.action.not_riding"),
        BUSY(false, "space.wildfires.return_capsule.action.busy"),
        NO_STATION(false, "space.wildfires.return_capsule.action.no_station"),
        NO_FUEL(false, "space.wildfires.return_capsule.action.no_fuel"),
        NO_CORE(false, "space.wildfires.return_capsule.action.no_core"),
        NO_DOCK(false, "space.wildfires.return_capsule.action.no_dock"),
        NO_SURFACE(false, "space.wildfires.return_capsule.action.no_surface"),
        NOT_DOCKED(false, "space.wildfires.return_capsule.action.not_docked"),
        RECOVERY_REQUIRED(false, "space.wildfires.return_capsule.action.recovery"),
        INVALID_STATE(false, "space.wildfires.return_capsule.action.invalid_state");

        private final boolean successful;
        private final String translationKey;

        ActionResult(boolean successful, String translationKey) {
            this.successful = successful;
            this.translationKey = translationKey;
        }

        public boolean successful() { return successful; }
        public String translationKey() { return translationKey; }
    }

    private record SurfaceBinding(ResourceLocation dimension, ServerLevel level) {
    }
}
