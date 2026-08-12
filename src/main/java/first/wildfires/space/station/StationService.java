package first.wildfires.space.station;

import first.wildfires.space.celestial.CelestialRegistrySnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Sole mutation boundary for global station records. Commands and future block entities call this service. */
public final class StationService {

    private StationService() {
    }

    public static OperationResult create(SpaceSavedData data, UUID stationId, String name, UUID owner,
                                         ResourceLocation initialBody,
                                         CelestialRegistrySnapshot definitions, long gameTime) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(initialBody, "initialBody");
        Objects.requireNonNull(definitions, "definitions");
        if (!data.writable()) {
            return rejected(OperationStatus.DATA_READ_ONLY, data.writeBlockReason().orElse("read-only"));
        }
        if (data.station(stationId).isPresent()) {
            return rejected(OperationStatus.DUPLICATE_STATION, "Station UUID already exists");
        }
        if (data.stations().size() >= SpaceConstants.MAX_STATIONS) {
            return rejected(OperationStatus.STATION_LIMIT, "Station limit reached");
        }
        if (definitions.lookup(definitions.generation(), initialBody).status()
                != CelestialRegistrySnapshot.LookupStatus.PRESENT) {
            return rejected(OperationStatus.BODY_UNAVAILABLE,
                    "Initial celestial definition is unavailable: " + initialBody);
        }
        try {
            StationRegionAllocator.Allocation allocation = StationRegionAllocator.allocate(
                    data.occupiedRegions(), data.retiredRegions(), data.nextRegionOrdinal());
            StationRecord station = StationRecord.create(stationId, name, owner,
                    allocation.region(), initialBody, requireGameTime(gameTime));
            data.createStation(station, allocation.nextOrdinal(), Optional.of(owner), gameTime);
            return success(station, "Station created");
        } catch (IllegalArgumentException exception) {
            return rejected(OperationStatus.INVALID_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            return rejected(OperationStatus.REGION_UNAVAILABLE, exception.getMessage());
        }
    }

    public static OperationResult rename(SpaceSavedData data, UUID stationId, UUID actor,
                                         boolean administrator, String name, long gameTime) {
        StationRecord station = data.station(stationId).orElse(null);
        OperationResult precondition = mutationPrecondition(data, station, actor, administrator, true);
        if (precondition != null) {
            return precondition;
        }
        try {
            StationRecord updated = station.renamed(name, requireGameTime(gameTime));
            if (updated == station) {
                return noChange(station, "Station name is unchanged");
            }
            data.replaceStation(updated, Optional.of(actor), StationAuditEntry.Action.RENAMED,
                    updated.name(), gameTime);
            return success(updated, "Station renamed");
        } catch (IllegalArgumentException exception) {
            return rejected(OperationStatus.INVALID_REQUEST, exception.getMessage());
        }
    }

    public static OperationResult setMember(SpaceSavedData data, UUID stationId, UUID actor,
                                            boolean administrator, UUID member,
                                            Optional<StationPermission> permission, long gameTime) {
        StationRecord station = data.station(stationId).orElse(null);
        OperationResult precondition = mutationPrecondition(data, station, actor, administrator, true);
        if (precondition != null) {
            return precondition;
        }
        try {
            StationRecord updated = station.withMember(member, permission, requireGameTime(gameTime));
            if (updated == station) {
                return noChange(station, "Station member is unchanged");
            }
            String detail = permission.map(value -> member + "=" + value.id())
                    .orElseGet(() -> member + "=removed");
            data.replaceStation(updated, Optional.of(actor), StationAuditEntry.Action.MEMBER_CHANGED,
                    detail, gameTime);
            return success(updated, "Station member changed");
        } catch (IllegalArgumentException exception) {
            return rejected(OperationStatus.INVALID_REQUEST, exception.getMessage());
        }
    }

    public static OperationResult applyJourneyState(SpaceSavedData data, UUID stationId, UUID actor,
                                                     StationJourneyService.State state,
                                                     StationStatus status, long gameTime) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(status, "status");
        StationRecord station = data.station(stationId).orElse(null);
        OperationResult precondition = mutationPrecondition(data, station, actor, false, false);
        if (precondition != null) {
            return precondition;
        }
        if (!station.mayOperate(actor)) {
            return rejected(OperationStatus.PERMISSION_DENIED, "Station operation permission denied");
        }
        try {
            StationRecord updated = station.withJourneyState(state, status, requireGameTime(gameTime));
            if (updated == station) {
                return noChange(station, "Journey state is unchanged");
            }
            data.replaceStation(updated, Optional.of(actor),
                    StationAuditEntry.Action.JOURNEY_CHANGED,
                    "journey=" + status.id(), gameTime);
            StationJourneyTicker.track(updated);
            return success(updated, "Journey state changed");
        } catch (IllegalArgumentException exception) {
            return rejected(OperationStatus.INVALID_REQUEST, exception.getMessage());
        }
    }

    /** Applies an automatic server tick transition without inventing a player actor. */
    public static OperationResult applyJourneyStateSystem(SpaceSavedData data, UUID stationId,
                                                           StationJourneyService.State state,
                                                           StationStatus status, long gameTime) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(status, "status");
        if (!data.writable()) {
            return rejected(OperationStatus.DATA_READ_ONLY,
                    data.writeBlockReason().orElse("Space data is read-only"));
        }
        StationRecord station = data.station(stationId).orElse(null);
        if (station == null) {
            return rejected(OperationStatus.UNKNOWN_STATION, "Unknown station UUID");
        }
        try {
            StationRecord updated = station.withJourneyState(state, status, requireGameTime(gameTime));
            if (updated == station) {
                return noChange(station, "Journey state is unchanged");
            }
            data.replaceStation(updated, Optional.empty(), StationAuditEntry.Action.JOURNEY_CHANGED,
                    "journey=" + status.id(), gameTime);
            StationJourneyTicker.track(updated);
            return success(updated, "Journey state changed");
        } catch (IllegalArgumentException exception) {
            return rejected(OperationStatus.INVALID_REQUEST, exception.getMessage());
        }
    }

    public static OperationResult remove(SpaceSavedData data, UUID stationId, UUID actor,
                                         boolean administrator, long gameTime) {
        StationRecord station = data.station(stationId).orElse(null);
        OperationResult precondition = mutationPrecondition(data, station, actor, administrator, true);
        if (precondition != null) {
            return precondition;
        }
        try {
            data.removeStation(stationId, Optional.of(actor), requireGameTime(gameTime));
            return new OperationResult(OperationStatus.SUCCESS, Optional.empty(),
                    "Station removed; region retired");
        } catch (IllegalArgumentException exception) {
            return rejected(OperationStatus.INVALID_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            return rejected(OperationStatus.REGION_UNAVAILABLE, exception.getMessage());
        }
    }

    /** Explicit recovery never changes currentBody and therefore can never fall back to the overworld. */
    public static OperationResult recover(SpaceSavedData data, UUID stationId, UUID actor,
                                          boolean administrator, CelestialRegistrySnapshot definitions,
                                          long gameTime) {
        Objects.requireNonNull(definitions, "definitions");
        StationRecord station = data.station(stationId).orElse(null);
        OperationResult precondition = mutationPrecondition(data, station, actor, administrator, true);
        if (precondition != null) {
            return precondition;
        }
        CelestialRegistrySnapshot.Lookup lookup = definitions.lookup(
                definitions.generation(), station.currentBody());
        if (lookup.status() != CelestialRegistrySnapshot.LookupStatus.PRESENT) {
            return rejected(OperationStatus.RECOVERY_REQUIRES_VALID_BODY,
                    "Current celestial remains unavailable; explicit future reassignment is required: "
                            + station.currentBody());
        }
        StationRecord updated = station.withHealth(StationStatus.ACTIVE, Optional.empty(),
                requireGameTime(gameTime));
        if (updated == station) {
            return noChange(station, "Station is already active and orbiting");
        }
        data.replaceStation(updated, Optional.of(actor), StationAuditEntry.Action.RECOVERED,
                "body=" + station.currentBody(), gameTime);
        return success(updated, "Station recovered at its existing celestial");
    }

    /** Applies P2 definition invalidation to persistent station health without deleting or relocating stations. */
    public static int reconcileDefinitions(SpaceSavedData data, CelestialRegistrySnapshot definitions,
                                           long gameTime) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(definitions, "definitions");
        if (!data.writable() || definitions.generation() <= 0L) {
            return 0;
        }
        List<StationRecord> snapshot = new ArrayList<>(data.stations().values());
        int changes = 0;
        for (StationRecord station : snapshot) {
            StationStatus desired = requiredStatus(station, definitions);
            if (desired == StationStatus.ACTIVE) {
                continue;
            }
            Optional<StationJourney> journey = station.journey().map(StationService::faulted);
            StationRecord updated = station.withHealth(desired, journey, requireGameTime(gameTime));
            if (updated != station) {
                data.replaceStation(updated, Optional.empty(),
                        StationAuditEntry.Action.DEFINITIONS_RECONCILED,
                        "status=" + desired.id() + ",generation=" + definitions.generation(), gameTime);
                changes++;
            }
        }
        return changes;
    }

    public static Optional<BlockPos> safePoint(SpaceSavedData data, UUID stationId) {
        return data.station(stationId).map(StationRecord::primaryDock)
                .map(StationDockRecord::position);
    }

    private static StationStatus requiredStatus(StationRecord station,
                                                CelestialRegistrySnapshot definitions) {
        if (definitions.lookup(definitions.generation(), station.currentBody()).status()
                != CelestialRegistrySnapshot.LookupStatus.PRESENT) {
            return StationStatus.ORPHANED;
        }
        if (station.journey().isPresent()) {
            ResourceLocation target = station.journey().orElseThrow().toBody();
            if (definitions.lookup(definitions.generation(), target).status()
                    != CelestialRegistrySnapshot.LookupStatus.PRESENT) {
                return StationStatus.FAULTED;
            }
        }
        return StationStatus.ACTIVE;
    }

    private static StationJourney faulted(StationJourney journey) {
        if (journey.phase() == StationJourneyPhase.FAULTED) {
            return journey;
        }
        return journey.withPhase(StationJourneyPhase.FAULTED,
                journey.phaseStartedGameTime(), journey.phaseDurationTicks());
    }

    private static OperationResult mutationPrecondition(SpaceSavedData data, StationRecord station,
                                                        UUID actor, boolean administrator,
                                                        boolean managePermission) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(actor, "actor");
        if (!data.writable()) {
            return rejected(OperationStatus.DATA_READ_ONLY,
                    data.writeBlockReason().orElse("Space data is read-only"));
        }
        if (station == null) {
            return rejected(OperationStatus.UNKNOWN_STATION, "Unknown station UUID");
        }
        if (!administrator && managePermission && !station.mayManage(actor)) {
            return rejected(OperationStatus.PERMISSION_DENIED, "Station management permission denied");
        }
        return null;
    }

    private static long requireGameTime(long gameTime) {
        if (gameTime < 0L) {
            throw new IllegalArgumentException("Station service gameTime must be non-negative");
        }
        return gameTime;
    }

    private static OperationResult success(StationRecord station, String message) {
        return new OperationResult(OperationStatus.SUCCESS, Optional.of(station), message);
    }

    private static OperationResult noChange(StationRecord station, String message) {
        return new OperationResult(OperationStatus.NO_CHANGE, Optional.of(station), message);
    }

    private static OperationResult rejected(OperationStatus status, String message) {
        return new OperationResult(status, Optional.empty(), message == null ? status.name() : message);
    }

    public enum OperationStatus {
        SUCCESS,
        NO_CHANGE,
        DATA_READ_ONLY,
        STATION_LIMIT,
        DUPLICATE_STATION,
        UNKNOWN_STATION,
        PERMISSION_DENIED,
        BODY_UNAVAILABLE,
        REGION_UNAVAILABLE,
        RECOVERY_REQUIRES_VALID_BODY,
        INVALID_REQUEST
    }

    public record OperationResult(OperationStatus status, Optional<StationRecord> station, String message) {
        public OperationResult {
            Objects.requireNonNull(status, "status");
            station = Objects.requireNonNull(station, "station");
            message = Objects.requireNonNull(message, "message");
            if ((status == OperationStatus.SUCCESS || status == OperationStatus.NO_CHANGE)
                    && station.isEmpty() && status != OperationStatus.SUCCESS) {
                throw new IllegalArgumentException("Successful station operation result is missing station data");
            }
        }

        public boolean successful() {
            return status == OperationStatus.SUCCESS || status == OperationStatus.NO_CHANGE;
        }
    }
}
