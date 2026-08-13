package first.wildfires.space;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import first.wildfires.network.RequestStationTravelPacket;
import first.wildfires.space.celestial.CelestialDefinition;
import first.wildfires.space.celestial.CelestialRegistrySnapshot;
import first.wildfires.space.route.StationRouteDefinition;
import first.wildfires.space.route.StationRouteSnapshot;
import first.wildfires.space.route.StationTravelRequest;
import first.wildfires.space.route.StationTravelMode;
import first.wildfires.space.route.StationTransferTopology;
import first.wildfires.space.station.StationJumpTimings;
import first.wildfires.space.route.StationTravelResult;
import first.wildfires.space.route.StationTravelService;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationJourneyPhase;
import first.wildfires.space.station.StationJourneyService;
import first.wildfires.space.station.StationJourneyTickService;
import first.wildfires.space.station.StationRecord;
import first.wildfires.space.station.StationService;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Plain-Java P5 checks for atomic travel validation and the bounded C2S intent. */
public final class SpaceTravelSelfTest {

    private static final ResourceLocation EARTH = id("earth");
    private static final ResourceLocation MARS = id("mars");
    private static final ResourceLocation SUN = id("sun");
    private static final ResourceLocation MOON = id("moon");
    private static final ResourceLocation IO = id("io");
    private static final ResourceLocation EUROPA = id("europa");
    private static final ResourceLocation ROUTE_ID = id("earth_to_mars");
    private static final UUID OWNER = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("31000000-0000-0000-0000-000000000002");
    private static final UUID STATION = UUID.fromString("31000000-0000-0000-0000-000000000003");
    private static final UUID JOURNEY = UUID.fromString("31000000-0000-0000-0000-000000000004");
    private static final BlockPos COMPUTER = new BlockPos(2048, 64, 0);

    private SpaceTravelSelfTest() {
    }

    public static void main(String[] args) {
        requestPacketRoundTripsAndRejectsBadRevision();
        routeSnapshotRejectsInvalidEntriesAndSortsStableIds();
        stableOrbitExposesEveryNonStellarCelestialAsATransferTarget();
        localSystemTransfersAreBidirectionalNormalRoutes();
        transferTopologyClassifiesEverySatelliteDirection();
        disabledDataRouteDoesNotBlockStableOrbitTransfer();
        starsCannotBecomeRouteEndpointsThroughDataOrServerState();
        stationCreationRejectsStars();
        legacyStellarStationCannotRecoverOrRemainActive();
        everyPreconditionFailureIsAtomic();
        successfulStartPersistsExactlyOneSemanticMutation();
        activeJourneyRejectsASecondDepartureWithoutMutation();
        routeReloadRemovalFaultsWithoutMovingCurrentBody();
        jumpRequiresBothEnginesAndDifferentParentSystems();
        jumpUsesFixedRelativisticTimeBeforeNormalArrival();
        System.out.println("SpaceTravelSelfTest: all checks passed");
    }

    private static void requestPacketRoundTripsAndRejectsBadRevision() {
        StationTravelRequest request = new StationTravelRequest(COMPUTER, STATION, 7L, ROUTE_ID);
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        new RequestStationTravelPacket(request).encode(encoded);
        assertEquals(request, new RequestStationTravelPacket(encoded).request(), "request packet round-trip");
        StationTravelRequest jump = new StationTravelRequest(COMPUTER, STATION, 8L, ROUTE_ID,
                StationTravelMode.JUMP);
        FriendlyByteBuf jumpEncoded = new FriendlyByteBuf(Unpooled.buffer());
        new RequestStationTravelPacket(jump).encode(jumpEncoded);
        assertEquals(jump, new RequestStationTravelPacket(jumpEncoded).request(),
                "jump request packet preserves the requested travel mode");
        assertThrows(IllegalArgumentException.class,
                () -> new StationTravelRequest(COMPUTER, STATION, -1L, ROUTE_ID),
                "negative expected revision");
    }

    private static void routeSnapshotRejectsInvalidEntriesAndSortsStableIds() {
        CelestialRegistrySnapshot celestials = celestials();
        StationRouteDefinition z = route(id("z_route"), EARTH, MARS, true);
        StationRouteDefinition a = route(id("a_route"), EARTH, MARS, true);
        StationRouteDefinition wrongId = route(id("inside_id"), EARTH, MARS, true);
        StationRouteDefinition missingBody = route(id("missing_body"), EARTH, id("missing"), true);
        StationRouteDefinition starTarget = route(id("earth_to_sun"), EARTH, SUN, true);
        Map<ResourceLocation, StationRouteDefinition> source = new HashMap<>();
        source.put(z.id(), z);
        source.put(a.id(), a);
        source.put(id("outside_id"), wrongId);
        source.put(missingBody.id(), missingBody);
        source.put(starTarget.id(), starTarget);
        StationRouteSnapshot snapshot = StationRouteSnapshot.validate(1L, source, celestials);
        assertEquals(307, snapshot.definitions().size(), "accepted and generated route count");
        assertEquals(3, snapshot.rejected().size(), "rejected route count");
        assertEquals(18, snapshot.routesFrom(EARTH).size(), "Earth retains explicit and generated routes");
        assertTrue(snapshot.routesFrom(EARTH).contains(a) && snapshot.routesFrom(EARTH).contains(z),
                "explicit duplicate data-pack routes remain present");
    }

    private static void stableOrbitExposesEveryNonStellarCelestialAsATransferTarget() {
        CelestialRegistrySnapshot celestials = celestials();
        StationRouteSnapshot snapshot = StationRouteSnapshot.validate(1L, Map.of(), celestials);
        java.util.List<StationRouteDefinition> earthRoutes = snapshot.routesFrom(EARTH);
        assertEquals(17, earthRoutes.size(), "Earth reaches every other non-stellar celestial");
        StationRouteDefinition moon = snapshot.route(StationRouteDefinition.freeTransferId(EARTH, id("moon")))
                .orElseThrow();
        assertEquals(EARTH, moon.fromBody(), "generated route origin");
        assertEquals(id("moon"), moon.toBody(), "generated route target");
        assertEquals(StationRouteDefinition.FREE_TRANSFER_DEPARTURE_TICKS, moon.departureTicks(),
                "generated departure duration");
        assertEquals(StationRouteDefinition.FREE_TRANSFER_CRUISE_TICKS, moon.cruiseTicks(),
                "generated cruise duration");
        assertEquals(StationRouteDefinition.FREE_TRANSFER_ARRIVAL_TICKS, moon.arrivalTicks(),
                "generated arrival duration");
    }

    private static void disabledDataRouteDoesNotBlockStableOrbitTransfer() {
        CelestialRegistrySnapshot celestials = celestials();
        ResourceLocation moon = id("moon");
        StationRouteDefinition disabled = new StationRouteDefinition(id("earth_to_moon_disabled"),
                EARTH, moon, 1L, 1L, 1L, false);
        StationRouteSnapshot snapshot = StationRouteSnapshot.validate(1L,
                Map.of(disabled.id(), disabled), celestials);
        StationRouteDefinition generated = snapshot.route(StationRouteDefinition.freeTransferId(EARTH, moon))
                .orElseThrow();
        assertTrue(generated.enabled() && generated.connects(EARTH, moon),
                "a disabled data route cannot suppress the stable-orbit fallback transfer");
    }

    private static void localSystemTransfersAreBidirectionalNormalRoutes() {
        CelestialRegistrySnapshot celestials = celestials();
        StationRouteSnapshot snapshot = StationRouteSnapshot.validate(1L, Map.of(), celestials);
        List<ResourceLocation[]> localPairs = allLocalTransferPairs(celestials);
        assertEquals(28, localPairs.size(),
                "all built-in directed parent/moon and sibling-moon transfer pairs");
        for (ResourceLocation[] pair : localPairs) {
            StationRouteDefinition route = snapshot.route(
                    StationRouteDefinition.freeTransferId(pair[0], pair[1])).orElseThrow();
            assertTrue(route.enabled() && route.connects(pair[0], pair[1]),
                    "local transfer exists " + pair[0] + " -> " + pair[1]);
            assertTopology(celestials, pair[0], pair[1],
                    pair[0].equals(parentOf(celestials, pair[1]))
                            ? StationTransferTopology.PRIMARY_TO_SATELLITE
                            : pair[1].equals(parentOf(celestials, pair[0]))
                            ? StationTransferTopology.SATELLITE_TO_PRIMARY
                            : StationTransferTopology.SIBLING_SATELLITES);
            SpaceSavedData pairData = new SpaceSavedData();
            UUID pairStationId = UUID.nameUUIDFromBytes((pair[0] + "->" + pair[1])
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StationRecord pairStation = StationService.create(pairData, pairStationId,
                    "Local Edge", OWNER, pair[0], celestials, 20L).station().orElseThrow();
            StationTravelRequest pairRequest = new StationTravelRequest(COMPUTER, pairStationId,
                    pairStation.revision(), route.id(), StationTravelMode.NORMAL);
            assertEquals(StationTravelResult.Status.STARTED,
                    StationTravelService.start(pairData, OWNER, pairRequest, snapshot, celestials,
                            Context.ALL, 100L, UUID.randomUUID()).status(),
                    "normal local transfer starts " + pair[0] + " -> " + pair[1]);
        }

        SpaceSavedData data = new SpaceSavedData();
        StationRecord earthStation = StationService.create(data, STATION, "Local Transfer", OWNER,
                EARTH, celestials, 20L).station().orElseThrow();
        StationRouteDefinition earthToMoon = snapshot.route(
                StationRouteDefinition.freeTransferId(EARTH, MOON)).orElseThrow();
        StationTravelRequest normal = new StationTravelRequest(COMPUTER, STATION,
                earthStation.revision(), earthToMoon.id(), StationTravelMode.NORMAL);
        StationTravelResult started = StationTravelService.start(data, OWNER, normal, snapshot,
                celestials, Context.ALL, 100L, JOURNEY);
        assertEquals(StationTravelResult.Status.STARTED, started.status(),
                "parent-to-moon normal transfer starts");

        SpaceSavedData jumpData = new SpaceSavedData();
        StationRecord jumpStation = StationService.create(jumpData, UUID.randomUUID(), "Local Jump", OWNER,
                EARTH, celestials, 20L).station().orElseThrow();
        StationTravelRequest jump = new StationTravelRequest(COMPUTER, jumpStation.stationId(),
                jumpStation.revision(), earthToMoon.id(), StationTravelMode.JUMP);
        assertEquals(StationTravelResult.Status.JUMP_ROUTE_INELIGIBLE,
                StationTravelService.start(jumpData, OWNER, jump, snapshot, celestials,
                        Context.ALL, 100L, UUID.randomUUID()).status(),
                "parent-to-moon route remains a local normal transfer, not a jump");
    }

    private static List<ResourceLocation[]> allLocalTransferPairs(CelestialRegistrySnapshot celestials) {
        Map<ResourceLocation, List<ResourceLocation>> children = new HashMap<>();
        celestials.validation().resolved().values().stream()
                .filter(resolved -> resolved.definition().kind()
                        == first.wildfires.space.celestial.CelestialKind.MOON)
                .forEach(resolved -> resolved.definition().parent().ifPresent(parent ->
                        children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(resolved.id())));
        List<ResourceLocation[]> pairs = new ArrayList<>();
        children.entrySet().stream().sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    List<ResourceLocation> moons = entry.getValue().stream()
                            .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
                    for (ResourceLocation moon : moons) {
                        pairs.add(new ResourceLocation[]{entry.getKey(), moon});
                        pairs.add(new ResourceLocation[]{moon, entry.getKey()});
                    }
                    for (ResourceLocation origin : moons) {
                        for (ResourceLocation target : moons) {
                            if (!origin.equals(target)) {
                                pairs.add(new ResourceLocation[]{origin, target});
                            }
                        }
                    }
                });
        return List.copyOf(pairs);
    }

    private static ResourceLocation parentOf(CelestialRegistrySnapshot celestials,
                                             ResourceLocation body) {
        return celestials.lookup(celestials.generation(), body).definition().orElseThrow()
                .definition().parent().orElse(null);
    }

    private static void transferTopologyClassifiesEverySatelliteDirection() {
        CelestialRegistrySnapshot celestials = celestials();
        assertTopology(celestials, EARTH, MOON, StationTransferTopology.PRIMARY_TO_SATELLITE);
        assertTopology(celestials, MOON, EARTH, StationTransferTopology.SATELLITE_TO_PRIMARY);
        assertTopology(celestials, IO, EUROPA, StationTransferTopology.SIBLING_SATELLITES);
        assertTopology(celestials, EUROPA, IO, StationTransferTopology.SIBLING_SATELLITES);
        assertTopology(celestials, MOON, MARS, StationTransferTopology.INTER_SYSTEM);
        assertTopology(celestials, IO, EARTH, StationTransferTopology.INTER_SYSTEM);
    }

    private static void assertTopology(CelestialRegistrySnapshot celestials,
                                       ResourceLocation fromId, ResourceLocation toId,
                                       StationTransferTopology expected) {
        CelestialDefinition from = celestials.lookup(celestials.generation(), fromId)
                .definition().orElseThrow().definition();
        CelestialDefinition to = celestials.lookup(celestials.generation(), toId)
                .definition().orElseThrow().definition();
        StationTransferTopology actual = StationTransferTopology.classify(fromId, from, toId, to);
        assertEquals(expected, actual, "transfer topology " + fromId + " -> " + toId);
        assertEquals(expected == StationTransferTopology.INTER_SYSTEM, actual.isJumpEligible(),
                "jump eligibility follows transfer topology " + fromId + " -> " + toId);
    }

    private static void starsCannotBecomeRouteEndpointsThroughDataOrServerState() {
        CelestialRegistrySnapshot celestials = celestials();
        StationRouteDefinition starRoute = route(id("earth_to_sun"), EARTH, SUN, true);
        StationRouteSnapshot validated = StationRouteSnapshot.validate(1L,
                Map.of(starRoute.id(), starRoute), celestials);
        assertTrue(validated.route(starRoute.id()).isEmpty(),
                "data-pack route targeting a star is rejected");

        Fixture fixture = fixture(celestials);
        StationRouteSnapshot bypassedSnapshot = new StationRouteSnapshot(1L, celestials.generation(),
                Map.of(starRoute.id(), starRoute), Map.of());
        assertAtomic(fixture.data, () -> start(fixture.data, OWNER, fixture.request(starRoute.id()),
                        bypassedSnapshot, celestials, Context.ALL),
                StationTravelResult.Status.BODY_UNAVAILABLE);
    }

    private static void stationCreationRejectsStars() {
        CelestialRegistrySnapshot celestials = celestials();
        SpaceSavedData data = new SpaceSavedData();
        StationService.OperationResult result = StationService.create(data, STATION, "Star Test", OWNER,
                SUN, celestials, 20L);
        assertEquals(StationService.OperationStatus.BODY_UNAVAILABLE, result.status(),
                "a station cannot be created in a stellar orbit");
        assertTrue(data.stations().isEmpty(), "stellar station creation leaves no record");
    }

    private static void legacyStellarStationCannotRecoverOrRemainActive() {
        CelestialRegistrySnapshot celestials = celestials();
        SpaceSavedData activeData = new SpaceSavedData();
        StationService.create(activeData, STATION, "Legacy Star", OWNER, EARTH, celestials, 20L);
        CompoundTag legacyTag = activeData.save(new CompoundTag());
        legacyTag.getList("stations", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).putString("current_body", SUN.toString());
        SpaceSavedData data = SpaceSavedData.load(legacyTag);
        assertTrue(data.writable(), "legacy stellar station data remains readable for safe reconciliation");
        assertEquals(1, StationService.reconcileDefinitions(data, celestials, 21L),
                "stellar legacy station is reconciled out of active service");
        StationRecord orphaned = data.station(STATION).orElseThrow();
        assertEquals(first.wildfires.space.station.StationStatus.ORPHANED, orphaned.status(),
                "stellar legacy station becomes orphaned");
        StationService.OperationResult recovery = StationService.recover(data, STATION, OWNER,
                false, celestials, 22L);
        assertEquals(StationService.OperationStatus.RECOVERY_REQUIRES_VALID_BODY, recovery.status(),
                "stellar legacy station cannot be recovered without reassignment");
    }

    private static void everyPreconditionFailureIsAtomic() {
        CelestialRegistrySnapshot celestials = celestials();
        Fixture fixture = fixture(celestials);
        StationTravelRequest valid = fixture.request(ROUTE_ID);
        StationRouteSnapshot enabled = routes(route(ROUTE_ID, EARTH, MARS, true), celestials);

        SpaceSavedData blocked = SpaceSavedData.load(versionTag(999));
        assertEquals(StationTravelResult.Status.DATA_READ_ONLY,
                start(blocked, OWNER, valid, enabled, celestials, Context.ALL).status(),
                "read-only data checked first");

        assertAtomic(fixture.data, () -> start(fixture.data, OWNER,
                new StationTravelRequest(COMPUTER, UUID.randomUUID(), fixture.station.revision(), ROUTE_ID),
                enabled, celestials, Context.ALL), StationTravelResult.Status.UNKNOWN_STATION);
        assertAtomic(fixture.data, () -> start(fixture.data, OTHER, valid, enabled, celestials, Context.ALL),
                StationTravelResult.Status.PERMISSION_DENIED);
        assertAtomic(fixture.data, () -> start(fixture.data, OWNER,
                        new StationTravelRequest(COMPUTER, STATION, fixture.station.revision() + 1L, ROUTE_ID),
                        enabled, celestials, Context.ALL),
                StationTravelResult.Status.STALE_REVISION);
        assertAtomic(fixture.data, () -> start(fixture.data, OWNER, valid,
                        StationRouteSnapshot.empty(), celestials, Context.ALL),
                StationTravelResult.Status.ROUTE_UNAVAILABLE);
        assertAtomic(fixture.data, () -> start(fixture.data, OWNER, valid,
                        routes(route(ROUTE_ID, EARTH, MARS, false), celestials), celestials, Context.ALL),
                StationTravelResult.Status.ROUTE_DISABLED);
        assertAtomic(fixture.data, () -> start(fixture.data, OWNER, valid,
                        routes(route(ROUTE_ID, MARS, EARTH, true), celestials), celestials, Context.ALL),
                StationTravelResult.Status.WRONG_ORIGIN);

        StationRouteDefinition unavailable = route(ROUTE_ID, EARTH, id("unavailable"), true);
        StationRouteSnapshot unvalidated = new StationRouteSnapshot(1L, celestials.generation(),
                Map.of(ROUTE_ID, unavailable), Map.of());
        assertAtomic(fixture.data, () -> start(fixture.data, OWNER, valid, unvalidated,
                        celestials, Context.ALL),
                StationTravelResult.Status.BODY_UNAVAILABLE);
        assertAtomic(fixture.data, () -> start(fixture.data, OWNER, valid, enabled,
                        celestials, new Context(false, true, true)),
                StationTravelResult.Status.RETURN_CAPSULE_AWAY);
        assertAtomic(fixture.data, () -> start(fixture.data, OWNER, valid, enabled,
                        celestials, new Context(true, false, true)),
                StationTravelResult.Status.INVALID_COMPUTER);
        assertAtomic(fixture.data, () -> start(fixture.data, OWNER, valid, enabled,
                        celestials, new Context(true, true, false)),
                StationTravelResult.Status.NO_TEST_ENGINE);
    }

    private static void successfulStartPersistsExactlyOneSemanticMutation() {
        CelestialRegistrySnapshot celestials = celestials();
        Fixture fixture = fixture(celestials);
        long oldRevision = fixture.station.revision();
        int oldAudit = fixture.data.auditEntries().size();
        fixture.data.setDirty(false);
        StationTravelResult result = start(fixture.data, OWNER, fixture.request(ROUTE_ID),
                routes(route(ROUTE_ID, EARTH, MARS, true), celestials), celestials, Context.ALL);
        StationRecord updated = result.station().orElseThrow();
        assertEquals(StationTravelResult.Status.STARTED, result.status(), "successful departure");
        assertEquals(oldRevision + 1L, updated.revision(), "one departure revision");
        assertEquals(EARTH, updated.currentBody(), "departure keeps current body");
        assertEquals(StationJourneyPhase.DEPARTING, updated.journey().orElseThrow().phase(),
                "departure phase");
        assertEquals(oldAudit + 1, fixture.data.auditEntries().size(), "one departure audit");
        assertTrue(fixture.data.isDirty(), "successful departure marks data dirty");
    }

    private static void activeJourneyRejectsASecondDepartureWithoutMutation() {
        CelestialRegistrySnapshot celestials = celestials();
        Fixture fixture = fixture(celestials);
        StationRouteSnapshot routes = routes(route(ROUTE_ID, EARTH, MARS, true), celestials);
        StationTravelResult first = start(fixture.data, OWNER, fixture.request(ROUTE_ID),
                routes, celestials, Context.ALL);
        StationRecord active = first.station().orElseThrow();
        StationTravelRequest second = new StationTravelRequest(COMPUTER, STATION,
                active.revision(), ROUTE_ID);
        assertAtomic(fixture.data, () -> start(fixture.data, OWNER, second, routes,
                        celestials, Context.ALL),
                StationTravelResult.Status.STATION_UNAVAILABLE);
    }

    private static void routeReloadRemovalFaultsWithoutMovingCurrentBody() {
        CelestialRegistrySnapshot celestials = celestials();
        Fixture fixture = fixture(celestials);
        StationRouteSnapshot routes = routes(route(ROUTE_ID, EARTH, MARS, true), celestials);
        StationRecord active = start(fixture.data, OWNER, fixture.request(ROUTE_ID), routes,
                celestials, Context.ALL).station().orElseThrow();
        StationJourneyTickService.TickResult progress = StationJourneyTickService.advance(
                active, routes, celestials, 110L);
        assertEquals(false, progress.changed(), "progress-only tick remains read-only");

        StationJourneyTickService.TickResult removed = StationJourneyTickService.advance(
                active, StationRouteSnapshot.empty(), celestials, 110L);
        assertEquals(true, removed.changed(), "removed route changes journey health");
        assertEquals(true, removed.faulted(), "removed route faults journey");
        assertEquals(EARTH, removed.state().currentBody(), "removed route preserves current body");
        assertEquals(active.revision() + 1L, removed.state().revision(), "removed route fault revision");
        assertEquals(StationJourneyPhase.FAULTED,
                removed.state().journey().orElseThrow().phase(), "removed route fault phase");
    }

    private static void jumpRequiresBothEnginesAndDifferentParentSystems() {
        CelestialRegistrySnapshot celestials = celestials();
        Fixture fixture = fixture(celestials);
        StationRouteSnapshot routes = routes(route(ROUTE_ID, EARTH, MARS, true), celestials);
        StationTravelRequest jump = new StationTravelRequest(COMPUTER, STATION, fixture.station.revision(),
                ROUTE_ID, StationTravelMode.JUMP);
        assertAtomic(fixture.data, () -> StationTravelService.start(fixture.data, OWNER, jump, routes,
                        celestials, new Context(true, true, false, true), 100L, JOURNEY),
                StationTravelResult.Status.NO_TEST_ENGINE);
        assertAtomic(fixture.data, () -> StationTravelService.start(fixture.data, OWNER, jump, routes,
                        celestials, new Context(true, true, true, false), 100L, JOURNEY),
                StationTravelResult.Status.NO_JUMP_TEST_ENGINE);
        StationTravelResult result = StationTravelService.start(fixture.data, OWNER, jump, routes,
                celestials, new Context(true, true, true, true), 100L, JOURNEY);
        assertEquals(StationTravelMode.JUMP, result.station().orElseThrow().journey().orElseThrow().mode(),
                "jump request persists its mode");

        assertJumpEligibility(celestials, EARTH, MOON, false,
                "planet-to-own-moon jump is rejected");
        assertJumpEligibility(celestials, IO, EUROPA, false,
                "sibling moons of one parent planet cannot jump between each other");
        assertJumpEligibility(celestials, MOON, MARS, true,
                "a moon may jump to a different parent-planet system");
    }

    private static void assertJumpEligibility(CelestialRegistrySnapshot celestials,
                                              ResourceLocation from, ResourceLocation to,
                                              boolean expected, String message) {
        UUID stationId = UUID.randomUUID();
        UUID journeyId = UUID.randomUUID();
        ResourceLocation routeId = StationRouteDefinition.freeTransferId(from, to);
        StationRouteDefinition route = route(routeId, from, to, true);
        StationRouteSnapshot routes = routes(route, celestials);
        SpaceSavedData data = new SpaceSavedData();
        StationRecord station = StationService.create(data, stationId, "Jump Eligibility", OWNER,
                from, celestials, 20L).station().orElseThrow();
        StationTravelRequest request = new StationTravelRequest(COMPUTER, stationId,
                station.revision(), routeId, StationTravelMode.JUMP);
        StationTravelResult result = StationTravelService.start(data, OWNER, request, routes,
                celestials, Context.ALL, 100L, journeyId);
        assertEquals(expected ? StationTravelResult.Status.STARTED
                        : StationTravelResult.Status.JUMP_ROUTE_INELIGIBLE,
                result.status(), message);
    }

    private static void jumpUsesFixedRelativisticTimeBeforeNormalArrival() {
        StationRouteDefinition route = new StationRouteDefinition(ROUTE_ID, EARTH, MARS, 20L, 9_999L, 20L, true);
        StationJourneyService.TransitionResult started = StationJourneyService.start(
                StationJourneyService.State.orbiting(EARTH, 0L), route, StationTravelMode.JUMP,
                100L, JOURNEY, OWNER);
        StationJourneyService.TransitionResult afterDeparture = StationJourneyService.advance(started.state(), route, 120L);
        assertEquals(StationJourneyPhase.JUMP_ACCELERATING, phase(afterDeparture), "jump enters 3-second acceleration");
        assertEquals(StationJumpTimings.ACCELERATION_TICKS,
                afterDeparture.state().journey().orElseThrow().phaseDurationTicks(), "fixed acceleration duration");
        StationJourneyService.TransitionResult cruise = StationJourneyService.advance(afterDeparture.state(), route, 180L);
        assertEquals(StationJourneyPhase.JUMP_CRUISING, phase(cruise), "jump enters 8-second cruise");
        assertEquals(StationJumpTimings.CRUISE_TICKS, cruise.state().journey().orElseThrow().phaseDurationTicks(),
                "fixed cruise duration ignores route distance");
        StationJourneyService.TransitionResult deceleration = StationJourneyService.advance(cruise.state(), route, 340L);
        assertEquals(StationJourneyPhase.JUMP_DECELERATING, phase(deceleration), "jump enters 3-second deceleration");
        assertEquals(MARS, deceleration.state().currentBody(), "target is committed before deceleration/view expansion");
        StationJourneyService.TransitionResult arriving = StationJourneyService.advance(deceleration.state(), route, 400L);
        assertEquals(StationJourneyPhase.ARRIVING, phase(arriving), "jump hands off to normal target arrival");
    }

    private static StationTravelResult start(SpaceSavedData data, UUID actor,
                                             StationTravelRequest request, StationRouteSnapshot routes,
                                             CelestialRegistrySnapshot celestials, Context context) {
        return StationTravelService.start(data, actor, request, routes, celestials,
                context, 100L, JOURNEY);
    }

    private static void assertAtomic(SpaceSavedData data, java.util.function.Supplier<StationTravelResult> action,
                                     StationTravelResult.Status expected) {
        String before = data.save(new CompoundTag()).toString();
        int audits = data.auditEntries().size();
        boolean dirty = data.isDirty();
        StationTravelResult result = action.get();
        assertEquals(expected, result.status(), "rejection status " + expected);
        assertEquals(before, data.save(new CompoundTag()).toString(), "rejection NBT atomicity " + expected);
        assertEquals(audits, data.auditEntries().size(), "rejection audit atomicity " + expected);
        assertEquals(dirty, data.isDirty(), "rejection dirty atomicity " + expected);
    }

    private static Fixture fixture(CelestialRegistrySnapshot celestials) {
        SpaceSavedData data = new SpaceSavedData();
        StationService.OperationResult created = StationService.create(data, STATION, "P5 Test", OWNER,
                EARTH, celestials, 20L);
        StationRecord station = created.station().orElseThrow();
        return new Fixture(data, station);
    }

    private static StationRouteSnapshot routes(StationRouteDefinition route,
                                               CelestialRegistrySnapshot celestials) {
        return new StationRouteSnapshot(1L, celestials.generation(), Map.of(route.id(), route), Map.of());
    }

    private static StationRouteDefinition route(ResourceLocation id, ResourceLocation from,
                                                ResourceLocation to, boolean enabled) {
        return new StationRouteDefinition(id, from, to, 20L, 40L, 20L, enabled);
    }

    private static StationJourneyPhase phase(StationJourneyService.TransitionResult result) {
        return result.state().journey().orElseThrow().phase();
    }

    private static CelestialRegistrySnapshot celestials() {
        Map<ResourceLocation, CelestialDefinition> definitions = new HashMap<>();
        Path folder = Path.of("src", "main", "resources", "data", "wildfires", "wildfires", "celestials");
        try (var paths = Files.list(folder)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                try {
                    String name = path.getFileName().toString();
                    ResourceLocation id = id(name.substring(0, name.length() - 5));
                    CelestialDefinition definition = CelestialDefinition.CODEC.parse(JsonOps.INSTANCE,
                            JsonParser.parseString(Files.readString(path))).getOrThrow(false,
                            message -> { throw new AssertionError(message); });
                    definitions.put(id, definition);
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            });
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        return CelestialRegistrySnapshot.reload(CelestialRegistrySnapshot.empty(), 1L, definitions,
                Set.of(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")), resource -> false);
    }

    private static CompoundTag versionTag(int version) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("data_version", version);
        return tag;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("wildfires", path);
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action, String name) {
        try {
            action.run();
            throw new AssertionError(name + ": expected " + expected.getSimpleName());
        } catch (Throwable actual) {
            if (!expected.isInstance(actual)) {
                throw new AssertionError(name + ": got " + actual, actual);
            }
        }
    }

    private static void assertTrue(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }

    private static void assertEquals(boolean expected, boolean actual, String name) {
        if (expected != actual) throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }

    private static void assertEquals(long expected, long actual, String name) {
        if (expected != actual) throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }

    private static void assertEquals(int expected, int actual, String name) {
        if (expected != actual) throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    private record Fixture(SpaceSavedData data, StationRecord station) {
        private StationTravelRequest request(ResourceLocation route) {
            return new StationTravelRequest(COMPUTER, station.stationId(), station.revision(), route);
        }
    }

    private record Context(boolean capsules, boolean computer, boolean engine, boolean jumpEngine)
            implements StationTravelService.ValidationContext {
        private static final Context ALL = new Context(true, true, true, true);

        private Context(boolean capsules, boolean computer, boolean engine) {
            this(capsules, computer, engine, true);
        }

        @Override
        public boolean allReturnCapsulesDocked(StationRecord station) {
            return capsules;
        }

        @Override
        public boolean validControlComputer(StationRecord station, StationTravelRequest request) {
            return computer;
        }

        @Override
        public boolean hasLoadedTestEngine(StationRecord station) {
            return engine;
        }

        @Override
        public boolean hasLoadedJumpTestEngine(StationRecord station) {
            return jumpEngine;
        }
    }
}
