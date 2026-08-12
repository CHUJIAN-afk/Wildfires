package first.wildfires.space;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import first.wildfires.network.RequestStationTravelPacket;
import first.wildfires.space.celestial.CelestialDefinition;
import first.wildfires.space.celestial.CelestialRegistrySnapshot;
import first.wildfires.space.route.StationRouteDefinition;
import first.wildfires.space.route.StationRouteSnapshot;
import first.wildfires.space.route.StationTravelRequest;
import first.wildfires.space.route.StationTravelResult;
import first.wildfires.space.route.StationTravelService;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationJourneyPhase;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Plain-Java P5 checks for atomic travel validation and the bounded C2S intent. */
public final class SpaceTravelSelfTest {

    private static final ResourceLocation EARTH = id("earth");
    private static final ResourceLocation MARS = id("mars");
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
        everyPreconditionFailureIsAtomic();
        successfulStartPersistsExactlyOneSemanticMutation();
        activeJourneyRejectsASecondDepartureWithoutMutation();
        routeReloadRemovalFaultsWithoutMovingCurrentBody();
        System.out.println("SpaceTravelSelfTest: all checks passed");
    }

    private static void requestPacketRoundTripsAndRejectsBadRevision() {
        StationTravelRequest request = new StationTravelRequest(COMPUTER, STATION, 7L, ROUTE_ID);
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        new RequestStationTravelPacket(request).encode(encoded);
        assertEquals(request, new RequestStationTravelPacket(encoded).request(), "request packet round-trip");
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
        Map<ResourceLocation, StationRouteDefinition> source = new HashMap<>();
        source.put(z.id(), z);
        source.put(a.id(), a);
        source.put(id("outside_id"), wrongId);
        source.put(missingBody.id(), missingBody);
        StationRouteSnapshot snapshot = StationRouteSnapshot.validate(1L, source, celestials);
        assertEquals(2, snapshot.definitions().size(), "accepted route count");
        assertEquals(2, snapshot.rejected().size(), "rejected route count");
        assertEquals(java.util.List.of(a, z), snapshot.routesFrom(EARTH), "stable route sort");
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

    private record Context(boolean capsules, boolean computer, boolean engine)
            implements StationTravelService.ValidationContext {
        private static final Context ALL = new Context(true, true, true);

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
    }
}
