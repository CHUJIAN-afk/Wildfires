package first.wildfires.space.station;

import first.wildfires.space.celestial.CelestialRegistryRuntime;
import first.wildfires.space.celestial.CelestialRegistrySnapshot;
import first.wildfires.space.route.StationRouteRuntime;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.content.StationDriveIndex;
import first.wildfires.space.route.StationRouteSnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Tracks only stations with live journeys and advances them from server game time. */
public final class StationJourneyTicker {

    private static final Set<UUID> ACTIVE = new LinkedHashSet<>();
    private static MinecraftServer activeServer;

    private StationJourneyTicker() {
    }

    public static synchronized void track(StationRecord station) {
        if (station.journey().filter(journey -> journey.phase() != StationJourneyPhase.FAULTED).isPresent()) {
            ACTIVE.add(station.stationId());
        } else {
            ACTIVE.remove(station.stationId());
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        SpaceSavedData data = SpaceSavedData.get(server);
        initialize(server, data);
        long gameTime = server.overworld().getGameTime();
        StationRouteSnapshot routes = StationRouteRuntime.current();
        CelestialRegistrySnapshot celestials = CelestialRegistryRuntime.current();

        for (UUID stationId : activeSnapshot()) {
            StationRecord station = data.station(stationId).orElse(null);
            if (station == null || station.journey().isEmpty()) {
                untrack(stationId);
                continue;
            }
            if (station.journey().orElseThrow().phase() == StationJourneyPhase.FAULTED) {
                untrack(stationId);
                continue;
            }
            try {
                StationJourneyTickService.TickResult transition = StationJourneyTickService.advance(
                        station, routes, celestials, gameTime);
                if (!transition.changed()) {
                    continue;
                }
                StationService.OperationResult result = StationService.applyJourneyStateSystem(
                        data, stationId, transition.state(), transition.status(), gameTime);
                if (transition.faulted() || transition.state().journey().isEmpty()) {
                    net.minecraft.server.level.ServerLevel orbit = server.getLevel(SpaceDimensions.ORBIT);
                    if (orbit != null) {
                        StationDriveIndex.endBurn(orbit, station);
                    }
                }
                if (result.station().isEmpty()
                        || result.station().orElseThrow().journey().isEmpty()
                        || transition.faulted()) {
                    untrack(stationId);
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                untrack(stationId);
            }
        }
    }

    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        activeServer = null;
    }

    private static synchronized void initialize(MinecraftServer server, SpaceSavedData data) {
        if (activeServer == server) {
            return;
        }
        ACTIVE.clear();
        data.stations().values().forEach(StationJourneyTicker::track);
        activeServer = server;
    }

    private static synchronized java.util.List<UUID> activeSnapshot() {
        return new ArrayList<>(ACTIVE);
    }

    private static synchronized void untrack(UUID stationId) {
        ACTIVE.remove(stationId);
    }
}
