package first.wildfires.space.celestial;

import first.wildfires.space.station.StationJourney;
import first.wildfires.space.station.StationJourneyPhase;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Minimal immutable journey fields required to reproduce station-window visuals. */
public record ObservationJourney(ResourceLocation fromBody,
                                 ResourceLocation toBody,
                                 StationJourneyPhase phase,
                                 long phaseStartedGameTime,
                                 long phaseDurationTicks) {

    public ObservationJourney {
        Objects.requireNonNull(fromBody, "fromBody");
        Objects.requireNonNull(toBody, "toBody");
        Objects.requireNonNull(phase, "phase");
        if (fromBody.equals(toBody)) {
            throw new IllegalArgumentException("An observation journey cannot target its origin");
        }
        if (!phase.isJourneyPhase() || phaseStartedGameTime < 0L || phaseDurationTicks < 0L) {
            throw new IllegalArgumentException("Invalid observation journey phase or time range");
        }
    }

    public static ObservationJourney from(StationJourney journey) {
        Objects.requireNonNull(journey, "journey");
        return new ObservationJourney(journey.fromBody(), journey.toBody(), journey.phase(),
                journey.phaseStartedGameTime(), journey.phaseDurationTicks());
    }
}
