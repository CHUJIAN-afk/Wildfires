package first.wildfires.space.station;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Stable, serialization-safe phases for a fixed space station's journey. */
public enum StationJourneyPhase {

    ORBITING("orbiting", false),
    DEPARTING("departing", true),
    CRUISE("cruise", true),
    JUMP_ACCELERATING("jump_accelerating", true),
    JUMP_CRUISING("jump_cruising", true),
    JUMP_DECELERATING("jump_decelerating", true),
    ARRIVING("arriving", true),
    FAULTED("faulted", true);

    private static final Map<String, StationJourneyPhase> BY_ID;

    static {
        Map<String, StationJourneyPhase> phases = new LinkedHashMap<>();
        for (StationJourneyPhase phase : values()) {
            StationJourneyPhase previous = phases.put(phase.id, phase);
            if (previous != null) {
                throw new IllegalStateException("Duplicate station journey phase id: " + phase.id);
            }
        }
        BY_ID = Collections.unmodifiableMap(phases);
    }

    private final String id;
    private final boolean journeyPhase;

    StationJourneyPhase(String id, boolean journeyPhase) {
        this.id = id;
        this.journeyPhase = journeyPhase;
    }

    public String id() {
        return id;
    }

    /** Returns whether this phase may be stored inside an active {@link StationJourney}. */
    public boolean isJourneyPhase() {
        return journeyPhase;
    }

    /** True only for the relativistic portion between normal departure and normal arrival. */
    public boolean isJumpPhase() {
        return this == JUMP_ACCELERATING || this == JUMP_CRUISING || this == JUMP_DECELERATING;
    }

    public static Optional<StationJourneyPhase> fromId(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id));
    }
}
