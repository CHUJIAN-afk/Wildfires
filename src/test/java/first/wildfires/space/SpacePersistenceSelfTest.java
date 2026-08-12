package first.wildfires.space;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import first.wildfires.space.celestial.CelestialDefinition;
import first.wildfires.space.celestial.CelestialRegistrySnapshot;
import first.wildfires.space.route.StationRouteDefinition;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationAuditEntry;
import first.wildfires.space.station.StationPermission;
import first.wildfires.space.station.StationRecord;
import first.wildfires.space.station.StationRegion;
import first.wildfires.space.station.StationRegionAllocator;
import first.wildfires.space.station.StationService;
import first.wildfires.space.station.StationStatus;
import first.wildfires.space.station.StationJourneyService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Plain-Java P3 checks for global station persistence, allocation and recovery contracts. */
public final class SpacePersistenceSelfTest {

    private static final ResourceLocation EARTH = id("earth");
    private static final ResourceLocation MARS = id("mars");
    private static final ResourceLocation OVERWORLD = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "overworld");

    private SpacePersistenceSelfTest() {
    }

    public static void main(String[] args) {
        regionMathIsSymmetricAndSpiralIsUnique();
        stationPermissionsRevisionAndDirtyStateAreAuthoritative();
        stationLimitsAreEnforced();
        savedDataRoundTripsAndNewerVersionsStayReadOnly();
        oneThousandStationsAllocateWithoutConflict();
        retiredRegionsAreNotAutomaticallyReused();
        removedDefinitionsFaultAndOrphanWithoutOverworldFallback();
        System.out.println("SpacePersistenceSelfTest: all checks passed");
    }

    private static void regionMathIsSymmetricAndSpiralIsUnique() {
        assertEquals(new StationRegion(0, 0), StationRegion.fromBlock(-1024, 0).orElseThrow(),
                "negative center-cell edge");
        assertEquals(new StationRegion(-1, 0), StationRegion.fromBlock(-1025, 0).orElseThrow(),
                "negative neighboring cell edge");
        assertEquals(new StationRegion(0, 0), StationRegion.fromBlock(1023, 0).orElseThrow(),
                "positive center-cell edge");
        assertEquals(new StationRegion(1, 0), StationRegion.fromBlock(1024, 0).orElseThrow(),
                "positive neighboring cell edge");

        Set<StationRegion> firstThousand = new HashSet<>();
        for (long index = 1L; index <= 1_000L; index++) {
            StationRegion region = StationRegionAllocator.regionAt(index);
            assertFalse(region.reserved(), "ordinary spiral never returns reserved origin");
            assertTrue(firstThousand.add(region), "spiral region is unique at index " + index);
            assertEquals(region, StationRegion.fromBlock(region.centerX(), region.centerZ()).orElseThrow(),
                    "region center reverse lookup");
        }
        assertEquals(1_000, firstThousand.size(), "first thousand spiral regions");
        assertThrows(IllegalArgumentException.class,
                () -> new StationRegion(Integer.MAX_VALUE, 0),
                "station region outside the Minecraft world is rejected");
    }

    private static void stationPermissionsRevisionAndDirtyStateAreAuthoritative() {
        SpaceSavedData data = new SpaceSavedData();
        CelestialRegistrySnapshot definitions = builtInSnapshot(1L, loadBuiltInCelestials());
        UUID stationId = uuid("permission-station");
        UUID owner = uuid("permission-owner");
        UUID manager = uuid("permission-manager");
        UUID stranger = uuid("permission-stranger");

        StationService.OperationResult created = StationService.create(data, stationId, "Permission Station",
                owner, EARTH, definitions, 100L);
        assertEquals(StationService.OperationStatus.SUCCESS, created.status(), "station creation");
        StationRecord station = created.station().orElseThrow();
        assertEquals(1L, station.revision(), "creation revision");
        assertTrue(station.mayManage(owner), "owner manages station");
        assertFalse(station.mayView(stranger), "stranger cannot view station");
        assertTrue(data.isDirty(), "create marks SavedData dirty");

        data.setDirty(false);
        StationService.OperationResult denied = StationService.rename(data, stationId, stranger,
                false, "Denied Rename", 101L);
        assertEquals(StationService.OperationStatus.PERMISSION_DENIED, denied.status(),
                "unauthorized rename rejected");
        assertFalse(data.isDirty(), "rejected mutation does not mark dirty");

        StationService.OperationResult memberChanged = StationService.setMember(data, stationId, owner,
                false, manager, Optional.of(StationPermission.MANAGER), 102L);
        assertEquals(StationService.OperationStatus.SUCCESS, memberChanged.status(), "manager added");
        assertTrue(memberChanged.station().orElseThrow().mayManage(manager), "manager permission matrix");
        long memberRevision = memberChanged.station().orElseThrow().revision();

        data.setDirty(false);
        StationService.OperationResult noChange = StationService.setMember(data, stationId, owner,
                false, manager, Optional.of(StationPermission.MANAGER), 103L);
        assertEquals(StationService.OperationStatus.NO_CHANGE, noChange.status(), "idempotent member update");
        assertEquals(memberRevision, noChange.station().orElseThrow().revision(), "no-op revision");
        assertFalse(data.isDirty(), "no-op mutation does not mark dirty");

        StationService.OperationResult renamed = StationService.rename(data, stationId, manager,
                false, "Managed Station", 104L);
        assertEquals(StationService.OperationStatus.SUCCESS, renamed.status(), "manager rename");
        assertEquals(memberRevision + 1L, renamed.station().orElseThrow().revision(), "rename revision");
        assertEquals(104L, renamed.station().orElseThrow().modifiedGameTime(), "modified gameTime");
    }

    private static void stationLimitsAreEnforced() {
        SpaceSavedData data = new SpaceSavedData();
        CelestialRegistrySnapshot definitions = builtInSnapshot(1L, loadBuiltInCelestials());
        UUID stationId = uuid("limit-station");
        UUID owner = uuid("limit-owner");
        assertEquals(StationService.OperationStatus.INVALID_REQUEST,
                StationService.create(data, uuid("blank-name-station"), " ", owner,
                        EARTH, definitions, 110L).status(),
                "blank station name rejected");
        assertEquals(StationService.OperationStatus.INVALID_REQUEST,
                StationService.create(data, uuid("long-name-station"), "x".repeat(65), owner,
                        EARTH, definitions, 111L).status(),
                "overlong station name rejected");

        StationService.create(data, stationId, "Limit Station", owner, EARTH, definitions, 112L);
        for (int index = 0; index < 128; index++) {
            StationService.OperationResult added = StationService.setMember(data, stationId, owner,
                    false, uuid("limit-member-" + index), Optional.of(StationPermission.MEMBER),
                    113L + index);
            assertEquals(StationService.OperationStatus.SUCCESS, added.status(),
                    "member within configured limit " + index);
        }
        StationService.OperationResult overflow = StationService.setMember(data, stationId, owner,
                false, uuid("limit-member-overflow"), Optional.of(StationPermission.MEMBER), 300L);
        assertEquals(StationService.OperationStatus.INVALID_REQUEST, overflow.status(),
                "member beyond configured limit rejected");
        assertEquals(128, data.station(stationId).orElseThrow().members().size(),
                "rejected member did not mutate station");
    }

    private static void savedDataRoundTripsAndNewerVersionsStayReadOnly() {
        SpaceSavedData data = new SpaceSavedData();
        CelestialRegistrySnapshot definitions = builtInSnapshot(1L, loadBuiltInCelestials());
        UUID stationId = uuid("roundtrip-station");
        UUID owner = uuid("roundtrip-owner");
        StationService.create(data, stationId, "Round Trip", owner, EARTH, definitions, 200L);
        StationService.setMember(data, stationId, owner, false, uuid("roundtrip-member"),
                Optional.of(StationPermission.OPERATOR), 201L);

        CompoundTag encoded = data.save(new CompoundTag());
        SpaceSavedData decoded = SpaceSavedData.load(encoded);
        assertTrue(decoded.writable(), "current SavedData version remains writable");
        assertEquals(data.stations(), decoded.stations(), "station NBT round-trip");
        assertEquals(data.retiredRegions(), decoded.retiredRegions(), "retired region round-trip");
        assertEquals(data.auditEntries(), decoded.auditEntries(), "audit round-trip");
        assertEquals(data.nextRegionOrdinal(), decoded.nextRegionOrdinal(), "allocator cursor round-trip");

        CompoundTag newer = encoded.copy();
        newer.putInt("data_version", SpaceSavedData.DATA_VERSION + 1);
        SpaceSavedData blocked = SpaceSavedData.load(newer);
        assertFalse(blocked.writable(), "newer SavedData version is read-only");
        assertThrows(IllegalStateException.class, blocked::setDirty,
                "newer SavedData cannot be marked dirty");
        assertThrows(IllegalStateException.class, () -> blocked.save(new CompoundTag()),
                "newer SavedData cannot be written back");

        CompoundTag negative = encoded.copy();
        negative.putInt("data_version", -1);
        assertFalse(SpaceSavedData.load(negative).writable(),
                "negative SavedData version is read-only");

        SpaceSavedData migratedEmpty = SpaceSavedData.load(new CompoundTag());
        assertTrue(migratedEmpty.writable(), "version-zero empty data migrates to current");
        assertEquals(0, migratedEmpty.stations().size(), "migrated empty station count");

        CompoundTag malformed = new CompoundTag();
        malformed.putInt("data_version", SpaceSavedData.DATA_VERSION);
        SpaceSavedData blockedMalformed = SpaceSavedData.load(malformed);
        assertFalse(blockedMalformed.writable(), "malformed current data is isolated read-only");

        CompoundTag wrongRootType = encoded.copy();
        wrongRootType.putString("stations", "not-a-list");
        assertFalse(SpaceSavedData.load(wrongRootType).writable(),
                "wrong current root NBT type is isolated read-only");

        CompoundTag malformedRecord = encoded.copy();
        ListTag stationTags = malformedRecord.getList("stations", Tag.TAG_COMPOUND);
        stationTags.getCompound(0).remove("members");
        assertFalse(SpaceSavedData.load(malformedRecord).writable(),
                "missing current station field is isolated read-only");

        CompoundTag malformedLegacy = new CompoundTag();
        malformedLegacy.putString("stations", "not-a-list");
        assertFalse(SpaceSavedData.load(malformedLegacy).writable(),
                "wrong legacy NBT type is not silently replaced during migration");
    }

    private static void oneThousandStationsAllocateWithoutConflict() {
        SpaceSavedData data = new SpaceSavedData();
        CelestialRegistrySnapshot definitions = builtInSnapshot(1L, loadBuiltInCelestials());
        Set<StationRegion> regions = new HashSet<>();
        for (int index = 0; index < 1_000; index++) {
            StationService.OperationResult result = StationService.create(data,
                    uuid("bulk-station-" + index), "Station " + index, uuid("bulk-owner-" + index),
                    EARTH, definitions, index);
            assertEquals(StationService.OperationStatus.SUCCESS, result.status(),
                    "bulk station create " + index);
            assertTrue(regions.add(result.station().orElseThrow().region()),
                    "bulk station region uniqueness " + index);
        }
        assertEquals(1_000, data.stations().size(), "bulk station count");
        assertEquals(1_000, regions.size(), "bulk region count");
        assertEquals(256, data.auditEntries().size(), "bounded station audit count");
    }

    private static void retiredRegionsAreNotAutomaticallyReused() {
        StationRegion first = StationRegionAllocator.allocate(Set.of(), Set.of(), 1L).region();
        StationRegionAllocator.Allocation second = StationRegionAllocator.allocate(Set.of(), Set.of(first), 1L);
        assertFalse(first.equals(second.region()), "retired region skipped from beginning of spiral");

        SpaceSavedData data = new SpaceSavedData();
        CelestialRegistrySnapshot definitions = builtInSnapshot(1L, loadBuiltInCelestials());
        UUID stationId = uuid("retired-station");
        UUID owner = uuid("retired-owner");
        StationRecord created = StationService.create(data, stationId, "Retired Station", owner,
                EARTH, definitions, 300L).station().orElseThrow();
        StationService.OperationResult removed = StationService.remove(data, stationId, owner,
                false, 301L);
        assertEquals(StationService.OperationStatus.SUCCESS, removed.status(), "station removal");
        assertTrue(data.station(stationId).isEmpty(), "removed station no longer active");
        assertTrue(data.retiredRegions().contains(created.region()), "removed region becomes tombstone");

        StationRecord replacement = StationService.create(data, uuid("replacement-station"),
                "Replacement", uuid("replacement-owner"), EARTH, definitions, 302L)
                .station().orElseThrow();
        assertFalse(created.region().equals(replacement.region()), "tombstoned region is not reused");
    }

    private static void removedDefinitionsFaultAndOrphanWithoutOverworldFallback() {
        Map<ResourceLocation, CelestialDefinition> builtIns = loadBuiltInCelestials();
        CelestialRegistrySnapshot generationOne = builtInSnapshot(1L, builtIns);
        SpaceSavedData data = new SpaceSavedData();
        UUID stationId = uuid("recovery-station");
        UUID owner = uuid("recovery-owner");
        StationRecord station = StationService.create(data, stationId, "Recovery", owner,
                EARTH, generationOne, 400L).station().orElseThrow();

        StationRouteDefinition route = new StationRouteDefinition(id("recovery_route"), EARTH, MARS,
                10L, 20L, 10L, true);
        StationJourneyService.TransitionResult journeyStarted = StationJourneyService.start(
                new StationJourneyService.State(station.currentBody(), station.journey(), station.revision()),
                route, 401L, uuid("recovery-journey"), owner);
        StationService.OperationResult journeyApplied = StationService.applyJourneyState(data, stationId,
                owner, journeyStarted.state(), StationStatus.ACTIVE, 401L);
        assertEquals(StationService.OperationStatus.SUCCESS, journeyApplied.status(), "journey persisted");
        assertEquals(StationAuditEntry.Action.JOURNEY_CHANGED,
                data.auditEntries().get(data.auditEntries().size() - 1).action(),
                "journey mutation has its own stable audit action");

        Map<ResourceLocation, CelestialDefinition> withoutMars = new HashMap<>(builtIns);
        withoutMars.remove(MARS);
        CelestialRegistrySnapshot generationTwo = CelestialRegistrySnapshot.reload(generationOne, 2L,
                withoutMars, Set.of(OVERWORLD), resource -> false);
        assertEquals(1, StationService.reconcileDefinitions(data, generationTwo, 402L),
                "removed destination faults station");
        StationRecord faulted = data.station(stationId).orElseThrow();
        assertEquals(StationStatus.FAULTED, faulted.status(), "faulted station status");
        assertEquals("faulted", faulted.journey().orElseThrow().phase().id(), "faulted journey phase");
        assertEquals(EARTH, faulted.currentBody(), "fault never changes current body");

        StationService.OperationResult recovered = StationService.recover(data, stationId, owner,
                false, generationTwo, 403L);
        assertEquals(StationService.OperationStatus.SUCCESS, recovered.status(), "fault recovery");
        assertEquals(StationStatus.ACTIVE, recovered.station().orElseThrow().status(),
                "recovered station status");
        assertTrue(recovered.station().orElseThrow().journey().isEmpty(), "recovery clears journey");

        Map<ResourceLocation, CelestialDefinition> withoutEarth = new HashMap<>(withoutMars);
        withoutEarth.remove(EARTH);
        CelestialRegistrySnapshot generationThree = CelestialRegistrySnapshot.reload(generationTwo, 3L,
                withoutEarth, Set.of(OVERWORLD), resource -> false);
        assertEquals(1, StationService.reconcileDefinitions(data, generationThree, 404L),
                "removed current body orphans station");
        StationRecord orphaned = data.station(stationId).orElseThrow();
        assertEquals(StationStatus.ORPHANED, orphaned.status(), "orphaned station status");
        assertEquals(EARTH, orphaned.currentBody(), "orphan keeps missing body id for administrator repair");
        StationService.OperationResult refused = StationService.recover(data, stationId, owner,
                false, generationThree, 405L);
        assertEquals(StationService.OperationStatus.RECOVERY_REQUIRES_VALID_BODY, refused.status(),
                "orphan recovery requires explicit future body reassignment");
        assertEquals(EARTH, data.station(stationId).orElseThrow().currentBody(),
                "orphan recovery never falls back to overworld");
    }

    private static CelestialRegistrySnapshot builtInSnapshot(
            long generation, Map<ResourceLocation, CelestialDefinition> definitions) {
        return CelestialRegistrySnapshot.reload(CelestialRegistrySnapshot.empty(), generation,
                definitions, Set.of(OVERWORLD), resource -> false);
    }

    private static Map<ResourceLocation, CelestialDefinition> loadBuiltInCelestials() {
        Path folder = Path.of("src", "main", "resources", "data", "wildfires", "wildfires", "celestials");
        Map<ResourceLocation, CelestialDefinition> definitions = new HashMap<>();
        try (var paths = Files.list(folder)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().forEach(path -> {
                        String filename = path.getFileName().toString();
                        ResourceLocation id = id(filename.substring(0, filename.length() - 5));
                        try {
                            CelestialDefinition definition = CelestialDefinition.CODEC.parse(
                                            JsonOps.INSTANCE, JsonParser.parseString(Files.readString(path)))
                                    .getOrThrow(false, message -> {
                                        throw new AssertionError("celestial decode failed: " + message);
                                    });
                            if (definitions.put(id, definition) != null) {
                                throw new AssertionError("duplicate built-in celestial: " + id);
                            }
                        } catch (IOException exception) {
                            throw new AssertionError("failed to read built-in celestial: " + path, exception);
                        }
                    });
        } catch (IOException exception) {
            throw new AssertionError("failed to list built-in celestial definitions", exception);
        }
        return Map.copyOf(definitions);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("wildfires", path);
    }

    private static UUID uuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertThrows(Class<? extends Throwable> expected, Runnable action, String name) {
        try {
            action.run();
            throw new AssertionError(name + ": expected " + expected.getSimpleName());
        } catch (Throwable actual) {
            if (!expected.isInstance(actual)) {
                throw new AssertionError(name + ": expected " + expected.getSimpleName()
                        + ", got " + actual.getClass().getSimpleName(), actual);
            }
        }
    }

    private static void assertTrue(boolean value, String name) {
        if (!value) {
            throw new AssertionError(name);
        }
    }

    private static void assertFalse(boolean value, String name) {
        assertTrue(!value, name);
    }

    private static void assertEquals(long expected, long actual, String name) {
        if (expected != actual) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String name) {
        if (expected != actual) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
