package first.wildfires.space.route;

import first.wildfires.space.celestial.CelestialRegistrySnapshot;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationJourneyService;
import first.wildfires.space.station.StationRecord;
import first.wildfires.space.station.StationService;
import first.wildfires.space.station.StationStatus;

import java.util.Objects;
import java.util.UUID;

/** Server-authoritative, failure-atomic departure transaction used only by the control computer. */
public final class StationTravelService {

    private StationTravelService() {
    }

    public static StationTravelResult start(SpaceSavedData data, UUID actor,
                                            StationTravelRequest request,
                                            StationRouteSnapshot routes,
                                            CelestialRegistrySnapshot celestials,
                                            ValidationContext context,
                                            long gameTime, UUID journeyId) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(routes, "routes");
        Objects.requireNonNull(celestials, "celestials");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(journeyId, "journeyId");

        if (!data.writable()) {
            return StationTravelResult.rejected(StationTravelResult.Status.DATA_READ_ONLY);
        }
        StationRecord station = data.station(request.stationId()).orElse(null);
        if (station == null) {
            return StationTravelResult.rejected(StationTravelResult.Status.UNKNOWN_STATION);
        }
        if (!station.mayOperate(actor)) {
            return StationTravelResult.rejected(StationTravelResult.Status.PERMISSION_DENIED);
        }
        if (station.revision() != request.expectedRevision()) {
            return StationTravelResult.rejected(StationTravelResult.Status.STALE_REVISION);
        }
        if (station.status() != StationStatus.ACTIVE || station.journey().isPresent()) {
            return StationTravelResult.rejected(StationTravelResult.Status.STATION_UNAVAILABLE);
        }

        StationRouteDefinition route = routes.route(request.routeId()).orElse(null);
        if (route == null) {
            return StationTravelResult.rejected(StationTravelResult.Status.ROUTE_UNAVAILABLE);
        }
        if (!route.enabled()) {
            return StationTravelResult.rejected(StationTravelResult.Status.ROUTE_DISABLED);
        }
        if (!route.fromBody().equals(station.currentBody())) {
            return StationTravelResult.rejected(StationTravelResult.Status.WRONG_ORIGIN);
        }
        if (!present(celestials, route.fromBody()) || !present(celestials, route.toBody())) {
            return StationTravelResult.rejected(StationTravelResult.Status.BODY_UNAVAILABLE);
        }
        if (!context.allReturnCapsulesDocked(station)) {
            return StationTravelResult.rejected(StationTravelResult.Status.RETURN_CAPSULE_AWAY);
        }
        if (!context.validControlComputer(station, request)) {
            return StationTravelResult.rejected(StationTravelResult.Status.INVALID_COMPUTER);
        }
        if (!context.hasLoadedTestEngine(station)) {
            return StationTravelResult.rejected(StationTravelResult.Status.NO_TEST_ENGINE);
        }

        try {
            StationJourneyService.State before = new StationJourneyService.State(
                    station.currentBody(), station.journey(), station.revision());
            StationJourneyService.TransitionResult transition = StationJourneyService.start(
                    before, route, gameTime, journeyId, actor);
            StationService.OperationResult persisted = StationService.applyJourneyState(
                    data, station.stationId(), actor, transition.state(), StationStatus.ACTIVE, gameTime);
            if (persisted.status() != StationService.OperationStatus.SUCCESS
                    || persisted.station().isEmpty()) {
                return StationTravelResult.rejected(StationTravelResult.Status.PERSISTENCE_REJECTED);
            }
            return StationTravelResult.started(persisted.station().orElseThrow());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return StationTravelResult.rejected(StationTravelResult.Status.PERSISTENCE_REJECTED);
        }
    }

    private static boolean present(CelestialRegistrySnapshot celestials,
                                   net.minecraft.resources.ResourceLocation body) {
        return celestials.lookup(celestials.generation(), body).status()
                == CelestialRegistrySnapshot.LookupStatus.PRESENT;
    }

    public interface ValidationContext {
        boolean allReturnCapsulesDocked(StationRecord station);

        boolean validControlComputer(StationRecord station, StationTravelRequest request);

        boolean hasLoadedTestEngine(StationRecord station);
    }
}
