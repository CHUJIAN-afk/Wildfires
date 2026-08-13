package first.wildfires.space.station;

import first.wildfires.space.route.StationRouteDefinition;
import first.wildfires.space.route.StationTravelMode;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure, deterministic state transitions for fixed-station journeys. */
public final class StationJourneyService {

    private StationJourneyService() {
    }

    public static TransitionResult start(State state, StationRouteDefinition route, long nowGameTime,
                                         UUID journeyId, UUID requestedBy) {
        return start(state, route, StationTravelMode.NORMAL, nowGameTime, journeyId, requestedBy);
    }

    public static TransitionResult start(State state, StationRouteDefinition route, StationTravelMode mode,
                                         long nowGameTime, UUID journeyId, UUID requestedBy) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(journeyId, "journeyId");
        Objects.requireNonNull(requestedBy, "requestedBy");
        requireNonNegative(nowGameTime, "nowGameTime");
        if (state.journey().isPresent()) {
            throw new IllegalStateException("Station already has an active journey");
        }
        if (!route.enabled()) {
            throw new IllegalArgumentException("Station route is disabled: " + route.id());
        }
        if (!route.fromBody().equals(state.currentBody())) {
            throw new IllegalArgumentException("Station is not at route origin " + route.fromBody()
                    + ": " + state.currentBody());
        }
        StationJourney journey = new StationJourney(journeyId, route.id(), route.fromBody(), route.toBody(),
                mode, StationJourneyPhase.DEPARTING, nowGameTime, route.departureTicks(), requestedBy);
        State next = new State(state.currentBody(), Optional.of(journey), incrementRevision(state.revision()));
        return new TransitionResult(next, List.of(Change.JOURNEY_STARTED));
    }

    public static TransitionResult advance(State state, StationRouteDefinition route, long nowGameTime) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(route, "route");
        requireNonNegative(nowGameTime, "nowGameTime");
        if (state.journey().isEmpty()) {
            return TransitionResult.unchanged(state);
        }

        StationJourney journey = state.journey().orElseThrow();
        requireMatchingRoute(journey, route);
        if (journey.phase() == StationJourneyPhase.FAULTED
                || nowGameTime < journey.phaseStartedGameTime()) {
            return TransitionResult.unchanged(state);
        }

        State workingState = state;
        StationJourney workingJourney = journey;
        List<Change> changes = new ArrayList<>(4);
        while (nowGameTime >= workingJourney.phaseEndGameTime()) {
            long nextStartedGameTime = workingJourney.phaseEndGameTime();
            switch (workingJourney.phase()) {
                case DEPARTING -> {
                    StationJourneyPhase next = workingJourney.mode() == StationTravelMode.JUMP
                            ? StationJourneyPhase.JUMP_ACCELERATING : StationJourneyPhase.CRUISE;
                    long duration = next == StationJourneyPhase.JUMP_ACCELERATING
                            ? StationJumpTimings.ACCELERATION_TICKS : route.cruiseTicks();
                    workingJourney = workingJourney.withPhase(next, nextStartedGameTime, duration);
                    workingState = new State(workingState.currentBody(), Optional.of(workingJourney),
                            incrementRevision(workingState.revision()));
                    changes.add(Change.PHASE_CHANGED);
                }
                case CRUISE -> {
                    workingJourney = workingJourney.withPhase(StationJourneyPhase.ARRIVING,
                            nextStartedGameTime, route.arrivalTicks());
                    workingState = new State(workingJourney.toBody(), Optional.of(workingJourney),
                            incrementRevision(workingState.revision()));
                    changes.add(Change.PHASE_CHANGED);
                    changes.add(Change.CURRENT_BODY_COMMITTED);
                }
                case JUMP_ACCELERATING -> {
                    workingJourney = workingJourney.withPhase(StationJourneyPhase.JUMP_CRUISING,
                            nextStartedGameTime, StationJumpTimings.CRUISE_TICKS);
                    workingState = new State(workingState.currentBody(), Optional.of(workingJourney),
                            incrementRevision(workingState.revision()));
                    changes.add(Change.PHASE_CHANGED);
                }
                case JUMP_CRUISING -> {
                    workingJourney = workingJourney.withPhase(StationJourneyPhase.JUMP_DECELERATING,
                            nextStartedGameTime, StationJumpTimings.DECELERATION_TICKS);
                    workingState = new State(workingJourney.toBody(), Optional.of(workingJourney),
                            incrementRevision(workingState.revision()));
                    changes.add(Change.PHASE_CHANGED);
                    changes.add(Change.CURRENT_BODY_COMMITTED);
                }
                case JUMP_DECELERATING -> {
                    workingJourney = workingJourney.withPhase(StationJourneyPhase.ARRIVING,
                            nextStartedGameTime, route.arrivalTicks());
                    workingState = new State(workingJourney.toBody(), Optional.of(workingJourney),
                            incrementRevision(workingState.revision()));
                    changes.add(Change.PHASE_CHANGED);
                }
                case ARRIVING -> {
                    workingState = new State(workingJourney.toBody(), Optional.empty(),
                            incrementRevision(workingState.revision()));
                    changes.add(Change.JOURNEY_COMPLETED);
                    return new TransitionResult(workingState, changes);
                }
                case FAULTED -> {
                    return changes.isEmpty()
                            ? TransitionResult.unchanged(state)
                            : new TransitionResult(workingState, changes);
                }
                case ORBITING -> throw new IllegalStateException("ORBITING cannot be stored in StationJourney");
            }
        }
        return changes.isEmpty()
                ? TransitionResult.unchanged(state)
                : new TransitionResult(workingState, changes);
    }

    private static void requireMatchingRoute(StationJourney journey, StationRouteDefinition route) {
        if (!journey.routeId().equals(route.id())
                || !journey.fromBody().equals(route.fromBody())
                || !journey.toBody().equals(route.toBody())) {
            throw new IllegalArgumentException("Route does not match active station journey: " + route.id());
        }
    }

    private static long incrementRevision(long revision) {
        try {
            return Math.incrementExact(revision);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Station revision overflow", exception);
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }

    public enum Change {
        JOURNEY_STARTED,
        PHASE_CHANGED,
        CURRENT_BODY_COMMITTED,
        JOURNEY_COMPLETED
    }

    public record State(ResourceLocation currentBody, Optional<StationJourney> journey, long revision) {

        public State {
            Objects.requireNonNull(currentBody, "currentBody");
            journey = Objects.requireNonNull(journey, "journey");
            requireNonNegative(revision, "revision");
            journey.ifPresent(activeJourney -> {
                ResourceLocation expectedBody = activeJourney.phase() == StationJourneyPhase.ARRIVING
                        || activeJourney.phase() == StationJourneyPhase.JUMP_DECELERATING
                        ? activeJourney.toBody()
                        : activeJourney.fromBody();
                if (activeJourney.phase() != StationJourneyPhase.FAULTED
                        && !currentBody.equals(expectedBody)) {
                    throw new IllegalArgumentException("Current body does not match journey phase "
                            + activeJourney.phase().id() + ": " + currentBody + " != " + expectedBody);
                }
            });
        }

        public static State orbiting(ResourceLocation currentBody, long revision) {
            return new State(currentBody, Optional.empty(), revision);
        }
    }

    public record TransitionResult(State state, List<Change> changes) {

        public TransitionResult {
            Objects.requireNonNull(state, "state");
            changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        }

        public boolean changed() {
            return !changes.isEmpty();
        }

        private static TransitionResult unchanged(State state) {
            return new TransitionResult(state, List.of());
        }
    }
}
