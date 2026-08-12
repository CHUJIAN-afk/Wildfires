package first.wildfires.space.station;

import first.wildfires.space.celestial.CelestialRegistrySnapshot;
import first.wildfires.space.route.StationRouteDefinition;
import first.wildfires.space.route.StationRouteSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Pure reload-aware decision for one active station; persistence remains in the ticker. */
public final class StationJourneyTickService {

    private StationJourneyTickService() {
    }

    public static TickResult advance(StationRecord station, StationRouteSnapshot routes,
                                     CelestialRegistrySnapshot celestials, long gameTime) {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(routes, "routes");
        Objects.requireNonNull(celestials, "celestials");
        if (gameTime < 0L) {
            throw new IllegalArgumentException("Journey tick gameTime must be non-negative");
        }
        if (station.journey().isEmpty()
                || station.journey().orElseThrow().phase() == StationJourneyPhase.FAULTED) {
            return TickResult.unchanged(station);
        }

        StationJourney journey = station.journey().orElseThrow();
        StationRouteDefinition route = routes.route(journey.routeId()).orElse(null);
        if (!validRoute(route, journey, celestials)) {
            StationJourney faulted = journey.withPhase(StationJourneyPhase.FAULTED,
                    journey.phaseStartedGameTime(), journey.phaseDurationTicks());
            StationJourneyService.State state = new StationJourneyService.State(
                    station.currentBody(), Optional.of(faulted), Math.incrementExact(station.revision()));
            return new TickResult(state, StationStatus.FAULTED, true, true);
        }

        StationJourneyService.TransitionResult transition = StationJourneyService.advance(
                new StationJourneyService.State(station.currentBody(), station.journey(), station.revision()),
                route, gameTime);
        return transition.changed()
                ? new TickResult(transition.state(), StationStatus.ACTIVE, true, false)
                : TickResult.unchanged(station);
    }

    private static boolean validRoute(StationRouteDefinition route, StationJourney journey,
                                      CelestialRegistrySnapshot celestials) {
        return route != null && route.enabled()
                && route.id().equals(journey.routeId())
                && route.fromBody().equals(journey.fromBody())
                && route.toBody().equals(journey.toBody())
                && present(celestials, route.fromBody())
                && present(celestials, route.toBody());
    }

    private static boolean present(CelestialRegistrySnapshot celestials, ResourceLocation id) {
        return celestials.lookup(celestials.generation(), id).status()
                == CelestialRegistrySnapshot.LookupStatus.PRESENT;
    }

    public record TickResult(StationJourneyService.State state, StationStatus status,
                             boolean changed, boolean faulted) {

        public TickResult {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(status, "status");
            if (faulted && (!changed || status != StationStatus.FAULTED
                    || state.journey().filter(journey -> journey.phase() == StationJourneyPhase.FAULTED)
                    .isEmpty())) {
                throw new IllegalArgumentException("Faulted tick result has inconsistent state");
            }
        }

        private static TickResult unchanged(StationRecord station) {
            return new TickResult(new StationJourneyService.State(station.currentBody(),
                    station.journey(), station.revision()), station.status(), false, false);
        }
    }
}
