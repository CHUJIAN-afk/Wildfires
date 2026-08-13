package first.wildfires.space.celestial;

import first.wildfires.space.station.StationJourney;
import first.wildfires.space.station.StationJourneyPhase;
import first.wildfires.space.route.StationTravelMode;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Minimal immutable journey fields required to reproduce station-window visuals. */
public record ObservationJourney(ResourceLocation fromBody,
                                 ResourceLocation toBody,
                                 StationTravelMode mode,
                                 StationJourneyPhase phase,
                                 long phaseStartedGameTime,
                                 long phaseDurationTicks) {

    /** Legacy observation snapshots are ordinary journeys. */
    public ObservationJourney(ResourceLocation fromBody, ResourceLocation toBody,
                              StationJourneyPhase phase, long phaseStartedGameTime,
                              long phaseDurationTicks) {
        this(fromBody, toBody, StationTravelMode.NORMAL, phase, phaseStartedGameTime,
                phaseDurationTicks);
    }

    public ObservationJourney {
        Objects.requireNonNull(fromBody, "fromBody");
        Objects.requireNonNull(toBody, "toBody");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(mode, "mode");
        if (fromBody.equals(toBody)) {
            throw new IllegalArgumentException("An observation journey cannot target its origin");
        }
        if (!phase.isJourneyPhase() || phaseStartedGameTime < 0L || phaseDurationTicks < 0L) {
            throw new IllegalArgumentException("Invalid observation journey phase or time range");
        }
    }

    public static ObservationJourney from(StationJourney journey) {
        Objects.requireNonNull(journey, "journey");
        return new ObservationJourney(journey.fromBody(), journey.toBody(), journey.mode(), journey.phase(),
                journey.phaseStartedGameTime(), journey.phaseDurationTicks());
    }
}
