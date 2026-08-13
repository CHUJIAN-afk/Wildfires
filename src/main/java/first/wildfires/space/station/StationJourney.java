package first.wildfires.space.station;

import net.minecraft.resources.ResourceLocation;
import first.wildfires.space.route.StationTravelMode;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot of one active journey for a fixed space station. */
public record StationJourney(
        UUID journeyId,
        ResourceLocation routeId,
        ResourceLocation fromBody,
        ResourceLocation toBody,
        StationTravelMode mode,
        StationJourneyPhase phase,
        long phaseStartedGameTime,
        long phaseDurationTicks,
        UUID requestedBy) {

    public static final int SNAPSHOT_VERSION = 2;

    /** Source-compatible constructor for legacy normal journeys and tests. */
    public StationJourney(UUID journeyId, ResourceLocation routeId, ResourceLocation fromBody,
                          ResourceLocation toBody, StationJourneyPhase phase,
                          long phaseStartedGameTime, long phaseDurationTicks, UUID requestedBy) {
        this(journeyId, routeId, fromBody, toBody, StationTravelMode.NORMAL, phase,
                phaseStartedGameTime, phaseDurationTicks, requestedBy);
    }

    public StationJourney {
        Objects.requireNonNull(journeyId, "journeyId");
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(fromBody, "fromBody");
        Objects.requireNonNull(toBody, "toBody");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(requestedBy, "requestedBy");
        if (fromBody.equals(toBody)) {
            throw new IllegalArgumentException("A station journey cannot target its origin: " + fromBody);
        }
        if (!phase.isJourneyPhase() || (mode == StationTravelMode.NORMAL && phase.isJumpPhase())
                || (mode == StationTravelMode.JUMP && phase == StationJourneyPhase.CRUISE)) {
            throw new IllegalArgumentException("An active journey cannot store phase: " + phase.id());
        }
        if (phaseStartedGameTime < 0L) {
            throw new IllegalArgumentException("phaseStartedGameTime must be non-negative: "
                    + phaseStartedGameTime);
        }
        if (phaseDurationTicks < 0L) {
            throw new IllegalArgumentException("phaseDurationTicks must be non-negative: "
                    + phaseDurationTicks);
        }
        phaseEndGameTime(phaseStartedGameTime, phaseDurationTicks);
    }

    public long phaseEndGameTime() {
        return phaseEndGameTime(phaseStartedGameTime, phaseDurationTicks);
    }

    public double progressAt(long nowGameTime) {
        if (nowGameTime <= phaseStartedGameTime) {
            return phaseDurationTicks == 0L && nowGameTime == phaseStartedGameTime ? 1.0D : 0.0D;
        }
        if (phaseDurationTicks == 0L || nowGameTime >= phaseEndGameTime()) {
            return 1.0D;
        }
        return (nowGameTime - phaseStartedGameTime) / (double) phaseDurationTicks;
    }

    public StationJourney withPhase(StationJourneyPhase nextPhase, long startedGameTime, long durationTicks) {
        return new StationJourney(journeyId, routeId, fromBody, toBody, mode, nextPhase,
                startedGameTime, durationTicks, requestedBy);
    }

    /**
     * Minimal versioned P1 snapshot. Unknown fields are intentionally ignored when reading; unknown
     * versions and phase IDs are rejected instead of being guessed.
     */
    public Map<String, String> toSnapshot() {
        return Map.of(
                "schema_version", Integer.toString(SNAPSHOT_VERSION),
                "journey_id", journeyId.toString(),
                "route_id", routeId.toString(),
                "from_body", fromBody.toString(),
                "to_body", toBody.toString(),
                "mode", mode.id(),
                "phase", phase.id(),
                "phase_started_game_time", Long.toString(phaseStartedGameTime),
                "phase_duration_ticks", Long.toString(phaseDurationTicks),
                "requested_by", requestedBy.toString());
    }

    public static StationJourney fromSnapshot(Map<String, String> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        int version = parseInt(required(snapshot, "schema_version"), "schema_version");
        if (version != 1 && version != SNAPSHOT_VERSION) {
            throw new IllegalArgumentException("Unsupported station journey snapshot version: " + version);
        }
        return new StationJourney(
                parseUuid(required(snapshot, "journey_id"), "journey_id"),
                parseResourceLocation(required(snapshot, "route_id"), "route_id"),
                parseResourceLocation(required(snapshot, "from_body"), "from_body"),
                parseResourceLocation(required(snapshot, "to_body"), "to_body"),
                version == 1 ? StationTravelMode.NORMAL : StationTravelMode.fromId(required(snapshot, "mode"))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown station travel mode: "
                                + snapshot.get("mode"))),
                StationJourneyPhase.fromId(required(snapshot, "phase"))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Unknown station journey phase id: " + snapshot.get("phase"))),
                parseLong(required(snapshot, "phase_started_game_time"), "phase_started_game_time"),
                parseLong(required(snapshot, "phase_duration_ticks"), "phase_duration_ticks"),
                parseUuid(required(snapshot, "requested_by"), "requested_by"));
    }

    private static long phaseEndGameTime(long startedGameTime, long durationTicks) {
        try {
            return Math.addExact(startedGameTime, durationTicks);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Station journey phase end overflows long", exception);
        }
    }

    private static String required(Map<String, String> snapshot, String key) {
        String value = snapshot.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing station journey snapshot field: " + key);
        }
        return value;
    }

    private static ResourceLocation parseResourceLocation(String value, String field) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid resource id in " + field + ": " + value);
        }
        return id;
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid UUID in " + field + ": " + value, exception);
        }
    }

    private static long parseLong(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid long in " + field + ": " + value, exception);
        }
    }

    private static int parseInt(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer in " + field + ": " + value, exception);
        }
    }
}
