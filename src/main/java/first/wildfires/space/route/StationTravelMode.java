package first.wildfires.space.route;

import java.util.Locale;
import java.util.Optional;

/** Immutable player intent for one server-validated station departure. */
public enum StationTravelMode {

    NORMAL("normal"),
    JUMP("jump");

    private final String id;

    StationTravelMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<StationTravelMode> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (StationTravelMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
