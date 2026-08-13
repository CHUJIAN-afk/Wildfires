/*
 * Adapted from NTM: Space EntityRideableRocket, CelestialTeleporter and
 * TileEntityOrbitalStation.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: replaced modular rockets, navigation drives and hydrazine with one
 * water-fuelled return capsule; ported launch/landing/docking/undocking state boundaries to Forge
 * 1.20.1; added UUID-preserving passenger transfer, exactly-once fuel tickets, crash recovery and
 * bounded source/target chunk tickets for uninterrupted insertion and re-entry.
 */
package first.wildfires.space.capsule;

import first.wildfires.Wildfires;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.celestial.CelestialBindingValidator;
import first.wildfires.space.celestial.CelestialRegistryRuntime;
import first.wildfires.space.content.SpaceContentRegister;
import first.wildfires.space.content.StationCoreBlockEntity;
import first.wildfires.space.content.StationIdTapeItem;
import first.wildfires.space.content.StationCoreService;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import first.wildfires.space.station.StationService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Authoritative launch, transfer, docking and recovery service for the sole shuttle entity. */
public final class ReturnCapsuleService {

    private static final ResourceLocation EARTH = Wildfires.rl("earth");
    public static final int LAUNCH_TICKS = 100;
    public static final int INSERTION_TICKS = 60;
    public static final int APPROACH_TICKS = 100;
    public static final int UNDOCK_TICKS = 40;
    public static final int REENTRY_TICKS = 100;
    public static final int LANDING_TICKS = 100;
    private static final int TRANSFER_TICKET_RADIUS = 4;
    private static final int TRANSFER_TICKET_TIMEOUT_TICKS = 600;
    private static final TicketType<UUID> TRANSFER_TICKET = TicketType.create(
            "wildfires_return_capsule_transfer", Comparator.comparing(UUID::toString),
            TRANSFER_TICKET_TIMEOUT_TICKS);

    private ReturnCapsuleService() {
    }

    public static ActionResult requestPrimaryAction(ServerPlayer player, ReusableReturnCapsuleEntity capsule) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(capsule, "capsule");
        if (player.getVehicle() != capsule || capsule.getFirstPassenger() != player) {
            return ActionResult.NOT_RIDING;
        }
        ReturnCapsuleTransitionTicket pending = capsule.transitionTicket().orElse(null);
        if (pending != null) {
            if (pending.stage() == ReturnCapsuleTransitionTicket.Stage.COMMITTED
                    && capsule.fuelTank().reservation().isEmpty()) {
                // The fuel ledger already retains the committed ticket UUID as its replay
                // tombstone. Let a pilot depart immediately on the docking/landing completion
                // tick instead of exposing the one-tick persisted COMMITTED fence as BUSY.
                capsule.clearTransitionTicket();
                capsule.setPhaseTicks(0);
            } else {
                return ActionResult.BUSY;
            }
        }
        return switch (capsule.capsuleState()) {
            case SURFACE_LANDED -> prepareLaunch(player, capsule);
            case STATION_DOCKED -> prepareReturn(player, capsule);
            case RECOVERY_REQUIRED -> ActionResult.RECOVERY_REQUIRED;
            default -> ActionResult.INVALID_STATE;
        };
    }

    private static ActionResult prepareLaunch(ServerPlayer player, ReusableReturnCapsuleEntity capsule) {
        ResourceLocation surfaceDimension = player.serverLevel().dimension().location();
        UUID selected = capsule.stationId().orElse(null);
        StationRecord station = selected == null ? null
                : SpaceSavedData.get(player.server).station(selected).orElse(null);
        if (station == null || !station.mayOperate(player.getUUID())
                || !station.currentBody().equals(EARTH) || station.journey().isPresent()
                || !surfaceDimension.equals(ResourceLocation.fromNamespaceAndPath(
                "minecraft", "overworld"))) {
            return ActionResult.NO_STATION;
        }
        if (!validCore(player.server, station)) {
            return ActionResult.NO_CORE;
        }
        UUID ticketId = UUID.randomUUID();
        if (!capsule.reserveFuelTrip(ticketId)) {
            return ActionResult.NO_FUEL;
        }
        ReturnCapsuleTransitionTicket ticket = new ReturnCapsuleTransitionTicket(ticketId,
                station.stationId(), ReturnCapsuleTransitionTicket.Direction.TO_STATION,
                station.currentBody(), surfaceDimension, capsule.blockPosition(),
                SpaceDimensions.ORBIT.location(), station.primaryDock().position(), player.getUUID(),
                capsule.revision(), player.server.overworld().getGameTime(),
                ReturnCapsuleTransitionTicket.Stage.PREPARED);
        capsule.bindStation(station.stationId());
        capsule.setHomeSurface(surfaceDimension, capsule.blockPosition());
        capsule.setTransitionTicket(ticket);
        capsule.setCapsuleState(ReturnCapsuleState.SURFACE_LAUNCHING);
        StationService.OperationResult bound = StationService.setReturnCapsule(
                SpaceSavedData.get(player.server), station.stationId(),
                capsule.getUUID(), true, player.server.overworld().getGameTime());
        if (!bound.successful()) {
            capsule.rollbackFuelTrip(ticketId);
            capsule.clearTransitionTicket();
            capsule.clearStationBinding();
            capsule.setCapsuleState(ReturnCapsuleState.SURFACE_LANDED);
            return ActionResult.NO_STATION;
        }
        acquireTripTickets(player.server, ticket);
        return ActionResult.STARTED;
    }

    public static InteractionResult applyStationTape(Player player,
                                                     ReusableReturnCapsuleEntity capsule,
                                                     ItemStack tape) {
        if (capsule.transferProtected() || !capsule.getPassengers().isEmpty()
                || capsule.capsuleState() != ReturnCapsuleState.SURFACE_LANDED) {
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
        if (station == null || !station.mayOperate(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                    "space.wildfires.station_id_tape.invalid"), true);
            return InteractionResult.CONSUME;
        }
        UUID previous = capsule.stationId().orElse(null);
        if (previous != null && !previous.equals(stationId)) {
            StationService.setReturnCapsule(SpaceSavedData.get(serverPlayer.server), previous,
                    capsule.getUUID(), false, serverPlayer.server.overworld().getGameTime());
        }
        capsule.bindStation(stationId);
        player.displayClientMessage(Component.translatable(
                "space.wildfires.station_id_tape.applied", station.name()), true);
        return InteractionResult.CONSUME;
    }

    public static boolean tryRecoverAsItem(ReusableReturnCapsuleEntity capsule) {
        if (!(capsule.level() instanceof ServerLevel level) || capsule.transferProtected()
                || !capsule.getPassengers().isEmpty() || !capsule.capsuleState().interactive()
                || capsule.transitionTicket().isPresent() || capsule.fuelTank().reservation().isPresent()) {
            return false;
        }
        capsule.stationId().ifPresent(stationId -> StationService.setReturnCapsule(
                SpaceSavedData.get(level.getServer()), stationId, capsule.getUUID(), false,
                level.getServer().overworld().getGameTime()));
        ItemStack recovered = new ItemStack(SpaceContentRegister.REUSABLE_RETURN_CAPSULE_ITEM.get());
        recovered.getOrCreateTag().put("wildfires_capsule_fuel", capsule.saveFuelForItem());
        capsule.spawnAtLocation(recovered, 0.0F);
        capsule.clearStationBinding();
        capsule.discard();
        return true;
    }

    private static ActionResult prepareReturn(ServerPlayer player, ReusableReturnCapsuleEntity capsule) {
        UUID stationId = capsule.stationId().orElse(null);
        if (stationId == null || player.serverLevel().dimension() != SpaceDimensions.ORBIT) {
            return ActionResult.NOT_DOCKED;
        }
        StationRecord station = SpaceSavedData.get(player.server).station(stationId).orElse(null);
        if (station == null || !station.mayOperate(player.getUUID()) || station.journey().isPresent()
                || !capsule.getUUID().equals(findDockedCapsule(player.server, station).map(Entity::getUUID).orElse(null))) {
            return ActionResult.NOT_DOCKED;
        }
        CelestialBindingValidator.ResolvedDefinition body = CelestialRegistryRuntime.current()
                .lookup(CelestialRegistryRuntime.current().generation(), station.currentBody())
                .definition().orElse(null);
        if (body == null || !body.landingAvailable() || body.surfaceDimension().isEmpty()) {
            return ActionResult.NO_SURFACE;
        }
        ResourceLocation surfaceDimension = body.surfaceDimension().orElseThrow();
        if (!surfaceDimension.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))) {
            return ActionResult.NO_SURFACE;
        }
        ResourceLocation savedSurfaceDimension = capsule.homeSurfaceDimension().orElse(null);
        BlockPos surface = capsule.homeSurfacePosition().orElse(null);
        if (!surfaceDimension.equals(savedSurfaceDimension) || surface == null) return ActionResult.NO_SURFACE;
        UUID ticketId = UUID.randomUUID();
        if (!capsule.reserveFuelTrip(ticketId)) return ActionResult.NO_FUEL;
        ReturnCapsuleTransitionTicket ticket = new ReturnCapsuleTransitionTicket(ticketId, stationId,
                ReturnCapsuleTransitionTicket.Direction.TO_SURFACE, station.currentBody(),
                SpaceDimensions.ORBIT.location(), station.primaryDock().position(), surfaceDimension, surface,
                player.getUUID(), capsule.revision(), player.server.overworld().getGameTime(),
                ReturnCapsuleTransitionTicket.Stage.PREPARED);
        capsule.setTransitionTicket(ticket);
        capsule.setCapsuleState(ReturnCapsuleState.STATION_UNDOCKING);
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
            recover(capsule);
            return;
        }
        if (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.ROLLED_BACK) {
            capsule.clearTransitionTicket();
            capsule.setPhaseTicks(0);
            return;
        }
        if (!reconcileTransferBoundary(capsule, source, ticket)) {
            return;
        }
        int ticks = capsule.phaseTicks() + 1;
        capsule.setPhaseTicks(ticks);
        switch (capsule.capsuleState()) {
            case SURFACE_LAUNCHING -> {
                capsule.setNoGravity(true);
                placeSurfaceAscent(capsule, ticket, ticks);
                if (ticks >= LAUNCH_TICKS) advance(capsule, ReturnCapsuleState.ASCENT_TRANSITION);
            }
            case ASCENT_TRANSITION -> transferToStation(capsule, source, ticket);
            case ORBIT_INSERTION -> {
                StationRecord station = SpaceSavedData.get(source.getServer()).station(ticket.stationId()).orElse(null);
                if (station == null) { recover(capsule); break; }
                Vec3 dock = dockedPosition(station.primaryDock().position());
                placeAlong(capsule, orbitInsertionStart(dock), orbitApproachStart(dock),
                        phase(ticks, INSERTION_TICKS));
                if (ticks >= INSERTION_TICKS) advance(capsule, ReturnCapsuleState.STATION_APPROACH);
            }
            case STATION_APPROACH -> {
                StationRecord station = SpaceSavedData.get(source.getServer()).station(ticket.stationId()).orElse(null);
                if (station == null) { recover(capsule); break; }
                Vec3 target = dockedPosition(station.primaryDock().position());
                placeAlong(capsule, orbitApproachStart(target), target, phase(ticks, APPROACH_TICKS));
                if (ticks >= APPROACH_TICKS) {
                    capsule.setPos(target);
                    capsule.setDeltaMovement(Vec3.ZERO);
                    capsule.setNoGravity(true);
                    capsule.setCapsuleState(ReturnCapsuleState.STATION_DOCKED);
                    commit(capsule, ticket);
                }
            }
            case STATION_UNDOCKING -> {
                capsule.setNoGravity(true);
                Vec3 dock = dockedPosition(ticket.sourcePosition());
                placeAlong(capsule, dock, orbitUndockEnd(dock), phase(ticks, UNDOCK_TICKS));
                if (ticks >= UNDOCK_TICKS) advance(capsule, ReturnCapsuleState.DEORBIT);
            }
            case DEORBIT -> transferToSurface(capsule, source, ticket);
            case REENTRY -> {
                capsule.setNoGravity(true);
                Vec3 landed = surfaceLandedPosition(ticket);
                placeAlong(capsule, surfaceEntryPosition(source, ticket), surfaceLandingStart(landed),
                        phase(ticks, REENTRY_TICKS));
                if (ticks >= REENTRY_TICKS) advance(capsule, ReturnCapsuleState.SURFACE_LANDING);
            }
            case SURFACE_LANDING -> {
                Vec3 landed = surfaceLandedPosition(ticket);
                placeAlong(capsule, surfaceLandingStart(landed), landed, phase(ticks, LANDING_TICKS));
                if (ticks >= LANDING_TICKS) {
                    capsule.setPos(landed);
                    capsule.setDeltaMovement(Vec3.ZERO);
                    capsule.setNoGravity(false);
                    capsule.setCapsuleState(ReturnCapsuleState.SURFACE_LANDED);
                    commit(capsule, ticket);
                }
            }
            default -> { }
        }
    }

    private static void transferToStation(ReusableReturnCapsuleEntity capsule, ServerLevel source,
                                          ReturnCapsuleTransitionTicket ticket) {
        ServerLevel orbit = source.getServer().getLevel(SpaceDimensions.ORBIT);
        StationRecord station = SpaceSavedData.get(source.getServer()).station(ticket.stationId()).orElse(null);
        if (orbit == null || station == null || !validCore(source.getServer(), station)
                || !ticket.targetDimension().equals(SpaceDimensions.ORBIT.location())
                || !ticket.targetPosition().equals(station.primaryDock().position())) {
            recover(capsule); return;
        }
        BlockPos dock = ticket.targetPosition();
        Vec3 target = orbitInsertionStart(dockedPosition(dock));
        if (!transferTargetReady(orbit, ticket, target)) return;
        ReusableReturnCapsuleEntity moved = moveVehicleAndPassenger(capsule, orbit, target);
        if (moved == null) return;
        moved.setTransitionTicket(ticket.withStage(ReturnCapsuleTransitionTicket.Stage.TRANSFERRED));
        moved.setCapsuleState(ReturnCapsuleState.ORBIT_INSERTION);
    }

    private static void transferToSurface(ReusableReturnCapsuleEntity capsule, ServerLevel source,
                                          ReturnCapsuleTransitionTicket ticket) {
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ticket.targetDimension());
        ServerLevel surface = source.getServer().getLevel(key);
        if (surface == null) { recover(capsule); return; }
        BlockPos landing = ticket.targetPosition();
        Vec3 target = surfaceEntryPosition(surface, ticket);
        if (!transferTargetReady(surface, ticket, target)) return;
        ReusableReturnCapsuleEntity moved = moveVehicleAndPassenger(capsule, surface, target);
        if (moved == null) return;
        moved.setTransitionTicket(ticket.withStage(ReturnCapsuleTransitionTicket.Stage.TRANSFERRED));
        moved.setCapsuleState(ReturnCapsuleState.REENTRY);
    }

    private static ReusableReturnCapsuleEntity moveVehicleAndPassenger(ReusableReturnCapsuleEntity capsule,
                                                                        ServerLevel destination, Vec3 target) {
        Entity passenger = capsule.getFirstPassenger();
        if (!(passenger instanceof ServerPlayer player)) { recover(capsule); return null; }
        ReturnCapsuleTransitionTicket ticket = capsule.transitionTicket().orElse(null);
        if (ticket == null) { recover(capsule); return null; }
        acquireEndpointTicket(destination.getServer(), ticket.targetDimension(),
                ticket.targetPosition(), ticket);
        capsule.beginTransferDismount();
        player.stopRiding();
        capsule.endTransferDismount();
        Entity movedEntity = capsule.changeDimension(destination, exactTeleporter(target));
        if (!(movedEntity instanceof ReusableReturnCapsuleEntity moved)) {
            releaseEndpointTicket(destination.getServer(), ticket.targetDimension(),
                    ticket.targetPosition(), ticket);
            if (!capsule.isRemoved()) {
                player.startRiding(capsule, true);
                recover(capsule);
            }
            return null;
        }
        if (!moved.getUUID().equals(capsule.getUUID())) {
            recover(moved);
            player.teleportTo(destination, target.x, target.y + 1.15D, target.z,
                    player.getYRot(), player.getXRot());
            player.startRiding(moved, true);
            return null;
        }
        player.teleportTo(destination, target.x, target.y + 1.15D, target.z,
                player.getYRot(), player.getXRot());
        if (!player.startRiding(moved, true)) {
            recover(moved);
            return null;
        }
        releaseEndpointTicket(player.server, ticket.sourceDimension(), ticket.sourcePosition(), ticket);
        return moved;
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
            recover(capsule);
            return false;
        }
        if (!current.equals(ticket.targetDimension())) {
            return true;
        }
        if (ticket.stage() == ReturnCapsuleTransitionTicket.Stage.PREPARED) {
            capsule.setTransitionTicket(ticket.withStage(ReturnCapsuleTransitionTicket.Stage.TRANSFERRED));
        }
        if (ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_STATION
                && (capsule.capsuleState() == ReturnCapsuleState.ASCENT_TRANSITION
                || capsule.capsuleState() == ReturnCapsuleState.SURFACE_LAUNCHING)) {
            advance(capsule, ReturnCapsuleState.ORBIT_INSERTION);
        } else if (ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_SURFACE
                && (capsule.capsuleState() == ReturnCapsuleState.DEORBIT
                || capsule.capsuleState() == ReturnCapsuleState.STATION_UNDOCKING)) {
            advance(capsule, ReturnCapsuleState.REENTRY);
        }
        return true;
    }

    private static ITeleporter exactTeleporter(Vec3 target) {
        return new ITeleporter() {
            @Override
            public PortalInfo getPortalInfo(Entity entity, ServerLevel destination,
                                            java.util.function.Function<ServerLevel, PortalInfo> ignored) {
                return new PortalInfo(target, Vec3.ZERO, entity.getYRot(), entity.getXRot());
            }

            @Override
            public boolean playTeleportSound(ServerPlayer player, ServerLevel source, ServerLevel destination) {
                return false;
            }
        };
    }

    private static void advance(ReusableReturnCapsuleEntity capsule, ReturnCapsuleState state) {
        capsule.setPhaseTicks(0);
        capsule.setCapsuleState(state);
    }

    private static void placeSurfaceAscent(ReusableReturnCapsuleEntity capsule,
                                           ReturnCapsuleTransitionTicket ticket, int ticks) {
        Vec3 start = surfaceLandedPosition(ticket);
        Vec3 end = start.add(0.0D, 80.0D, 0.0D);
        placeAlong(capsule, start, end, phase(ticks, LAUNCH_TICKS));
    }

    private static Vec3 orbitInsertionStart(Vec3 dock) {
        return dock.add(0.0D, 80.0D, 36.0D);
    }

    private static Vec3 orbitApproachStart(Vec3 dock) {
        return dock.add(0.0D, 25.0D, 20.0D);
    }

    private static Vec3 orbitUndockEnd(Vec3 dock) {
        return dock.add(0.0D, 20.0D, 18.0D);
    }

    private static Vec3 surfaceLandedPosition(ReturnCapsuleTransitionTicket ticket) {
        BlockPos landing = ticket.surfacePosition();
        // The saved endpoint is the capsule's base block position, not the supporting terrain.
        return new Vec3(landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D);
    }

    /** Base of the NTM pod sits on top of the two-block immutable core volume. */
    private static Vec3 dockedPosition(BlockPos corePosition) {
        return Vec3.atCenterOf(corePosition).add(0.0D, 2.0D, 0.0D);
    }

    private static Vec3 surfaceEntryPosition(ServerLevel surface, ReturnCapsuleTransitionTicket ticket) {
        Vec3 landed = surfaceLandedPosition(ticket);
        return new Vec3(landed.x, Math.max(landed.y + 80.0D,
                surface.getMaxBuildHeight() - 16.0D), landed.z);
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
            if (ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_STATION) {
                StationRecord station = SpaceSavedData.get(level.getServer()).station(ticket.stationId()).orElse(null);
                if (station == null || !validCore(level.getServer(), station)) return ActionResult.NO_CORE;
                Vec3 dock = dockedPosition(station.primaryDock().position());
                capsule.setPos(dock);
                capsule.setCapsuleState(ReturnCapsuleState.STATION_DOCKED);
            } else {
                capsule.setPos(surfaceLandedPosition(ticket));
                capsule.setNoGravity(false);
                capsule.setCapsuleState(ReturnCapsuleState.SURFACE_LANDED);
            }
            commit(capsule, ticket);
            return ActionResult.RECOVERED;
        }
        if (current.equals(ticket.sourceDimension()) && capsule.rollbackFuelTrip(ticket.ticketId())) {
            if (ticket.direction() == ReturnCapsuleTransitionTicket.Direction.TO_STATION) {
                capsule.setPos(surfaceLandedPosition(ticket));
                capsule.setNoGravity(false);
                capsule.setCapsuleState(ReturnCapsuleState.SURFACE_LANDED);
            } else {
                StationRecord station = SpaceSavedData.get(level.getServer()).station(ticket.stationId()).orElse(null);
                if (station == null || !validCore(level.getServer(), station)) return ActionResult.NO_CORE;
                capsule.setPos(dockedPosition(station.primaryDock().position()));
                capsule.setNoGravity(true);
                capsule.setCapsuleState(ReturnCapsuleState.STATION_DOCKED);
            }
            capsule.clearTransitionTicket();
            capsule.setPhaseTicks(0);
            releaseTripTickets(level.getServer(), ticket);
            return ActionResult.RECOVERED;
        }
        return ActionResult.RECOVERY_REQUIRED;
    }

    private static void commit(ReusableReturnCapsuleEntity capsule, ReturnCapsuleTransitionTicket ticket) {
        if (capsule.commitFuelTrip(ticket.ticketId())) {
            if (capsule.level() instanceof ServerLevel level) {
                releaseTripTickets(level.getServer(), ticket);
            }
            // Keep a persisted COMMITTED ticket for at least one entity tick. The tank also keeps
            // the ticket UUID as a tombstone, so replay after a crash cannot drain a second bucket.
            capsule.setTransitionTicket(ticket.withStage(ReturnCapsuleTransitionTicket.Stage.COMMITTED));
            capsule.setPhaseTicks(0);
        } else {
            recover(capsule);
        }
    }

    private static void recover(ReusableReturnCapsuleEntity capsule) {
        capsule.setCapsuleState(ReturnCapsuleState.RECOVERY_REQUIRED);
        capsule.setNoGravity(true);
        capsule.setDeltaMovement(Vec3.ZERO);
        capsule.transitionTicket().ifPresent(ticket -> capsule.setTransitionTicket(
                ticket.withStage(ReturnCapsuleTransitionTicket.Stage.RECOVERY)));
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

    private static boolean validCore(MinecraftServer server, StationRecord station) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        if (orbit == null) return false;
        BlockPos position = station.primaryDock().position();
        return orbit.getBlockState(position).is(SpaceContentRegister.STATION_CORE.get())
                && orbit.getBlockEntity(position) instanceof StationCoreBlockEntity core
                && core.stationId().filter(station.stationId()::equals).isPresent()
                && StationCoreService.isComplete(orbit, position);
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
                .findFirst();
    }

    public static boolean allDocked(MinecraftServer server, StationRecord station) {
        ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
        if (orbit == null) return station.ownedReturnCapsules().isEmpty();
        return station.ownedReturnCapsules().stream().allMatch(id -> {
            Entity entity = orbit.getEntity(id);
            return entity instanceof ReusableReturnCapsuleEntity capsule
                    && capsule.stationId().filter(station.stationId()::equals).isPresent()
                    && capsule.capsuleState() == ReturnCapsuleState.STATION_DOCKED;
        });
    }

    public static double phaseProgress(ReusableReturnCapsuleEntity capsule, float partialTick) {
        int duration = switch (capsule.capsuleState()) {
            case SURFACE_LAUNCHING -> LAUNCH_TICKS;
            case ORBIT_INSERTION -> INSERTION_TICKS;
            case STATION_APPROACH -> APPROACH_TICKS;
            case STATION_UNDOCKING -> UNDOCK_TICKS;
            case REENTRY -> REENTRY_TICKS;
            case SURFACE_LANDING -> LANDING_TICKS;
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
}
