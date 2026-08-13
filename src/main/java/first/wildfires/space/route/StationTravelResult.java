package first.wildfires.space.route;

import first.wildfires.space.station.StationRecord;

import java.util.Objects;
import java.util.Optional;

/** Stable server result for one control-computer departure request. */
public record StationTravelResult(Status status, Optional<StationRecord> station) {

    public StationTravelResult {
        Objects.requireNonNull(status, "status");
        station = Objects.requireNonNull(station, "station");
        if (status == Status.STARTED && station.isEmpty()) {
            throw new IllegalArgumentException("A started journey must return the updated station");
        }
        if (status != Status.STARTED && station.isPresent()) {
            throw new IllegalArgumentException("A rejected journey cannot return mutated station data");
        }
    }

    public static StationTravelResult started(StationRecord station) {
        return new StationTravelResult(Status.STARTED, Optional.of(station));
    }

    public static StationTravelResult rejected(Status status) {
        if (status == Status.STARTED) {
            throw new IllegalArgumentException("Use started for successful travel results");
        }
        return new StationTravelResult(status, Optional.empty());
    }

    public boolean successful() {
        return status == Status.STARTED;
    }

    public enum Status {
        STARTED("started"),
        DATA_READ_ONLY("data_read_only"),
        UNKNOWN_STATION("unknown_station"),
        PERMISSION_DENIED("permission_denied"),
        STALE_REVISION("stale_revision"),
        STATION_UNAVAILABLE("station_unavailable"),
        ROUTE_UNAVAILABLE("route_unavailable"),
        ROUTE_DISABLED("route_disabled"),
        WRONG_ORIGIN("wrong_origin"),
        BODY_UNAVAILABLE("body_unavailable"),
        RETURN_CAPSULE_AWAY("return_capsule_away"),
        INVALID_COMPUTER("invalid_computer"),
        NO_TEST_ENGINE("no_test_engine"),
        NO_JUMP_TEST_ENGINE("no_jump_test_engine"),
        JUMP_ROUTE_INELIGIBLE("jump_route_ineligible"),
        PERSISTENCE_REJECTED("persistence_rejected");

        private final String id;

        Status(String id) {
            this.id = id;
        }

        public String translationKey() {
            return "space.wildfires.travel." + id;
        }
    }
}
