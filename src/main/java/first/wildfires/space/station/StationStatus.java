package first.wildfires.space.station;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Persisted station health independent of the active journey phase. */
public enum StationStatus {
    ACTIVE("active"),
    ORPHANED("orphaned"),
    FAULTED("faulted");

    private static final Map<String, StationStatus> BY_ID;

    static {
        Map<String, StationStatus> statuses = new LinkedHashMap<>();
        for (StationStatus status : values()) {
            if (statuses.put(status.id, status) != null) {
                throw new ExceptionInInitializerError("Duplicate station status id: " + status.id);
            }
        }
        BY_ID = Map.copyOf(statuses);
    }

    private final String id;

    StationStatus(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<StationStatus> fromId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }
}
