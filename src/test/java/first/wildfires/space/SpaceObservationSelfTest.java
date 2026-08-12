package first.wildfires.space;

import first.wildfires.client.space.SpaceClientState;
import first.wildfires.network.StationContextPacket;
import first.wildfires.network.StationRemovedPacket;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationContextResolver;
import first.wildfires.space.celestial.ObservationJourney;
import first.wildfires.space.station.StationJourneyPhase;
import first.wildfires.space.station.StationRegion;
import first.wildfires.space.station.StationStatus;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Plain-Java P4 checks for observation snapshots, client ordering and bounded packet codecs. */
public final class SpaceObservationSelfTest {

    private static final ResourceLocation EARTH = id("earth");
    private static final ResourceLocation MARS = id("mars");

    private SpaceObservationSelfTest() {
    }

    public static void main(String[] args) {
        stationRegionsDoNotLeakAcrossBoundaries();
        clientOrderingIsDeterministic();
        packetRoundTripsAndRejectsInvalidStableIds();
        System.out.println("SpaceObservationSelfTest: all checks passed");
    }

    private static void stationRegionsDoNotLeakAcrossBoundaries() {
        ObservationContext first = context("first", 4L, new StationRegion(1, 0), EARTH, 9L);
        ObservationContext second = context("second", 7L, new StationRegion(-1, 1), MARS, 9L);
        assertTrue(first.contains(first.region().minX(), first.region().minZ()), "first minimum edge");
        assertTrue(first.contains(first.region().maxX() + 0.999D, first.region().maxZ() + 0.999D),
                "first fractional maximum edge");
        assertFalse(first.contains(first.region().maxX() + 1.0D, first.region().centerZ()),
                "first outside edge");
        assertFalse(first.contains(second.region().centerX(), second.region().centerZ()),
                "different station region never aliases");
        assertFalse(first.contains(Double.NaN, 0.0D), "non-finite observer rejected");
    }

    private static void clientOrderingIsDeterministic() {
        SpaceClientState.install();
        SpaceClientState.clear();
        ObservationContext revisionFour = context("ordering", 4L, new StationRegion(1, 0), EARTH, 3L);
        assertEquals(ObservationContextResolver.UpdateResult.ACCEPTED,
                ObservationContextResolver.acceptClient(revisionFour), "initial context");
        assertEquals(ObservationContextResolver.UpdateResult.IDEMPOTENT,
                ObservationContextResolver.acceptClient(revisionFour), "duplicate context");

        ObservationContext conflict = new ObservationContext(revisionFour.stationId(), 4L,
                revisionFour.region(), MARS, revisionFour.status(), revisionFour.journey(), 3L);
        assertEquals(ObservationContextResolver.UpdateResult.CONFLICT,
                ObservationContextResolver.acceptClient(conflict), "same-version conflict");
        assertEquals(EARTH, SpaceClientState.current().orElseThrow().currentBody(),
                "conflict did not overwrite state");

        ObservationContext generationFour = context("ordering", 4L, new StationRegion(1, 0), EARTH, 4L);
        assertEquals(ObservationContextResolver.UpdateResult.ACCEPTED,
                ObservationContextResolver.acceptClient(generationFour), "registry generation advance");
        ObservationContext stale = context("ordering", 3L, new StationRegion(1, 0), EARTH, 5L);
        assertEquals(ObservationContextResolver.UpdateResult.STALE,
                ObservationContextResolver.acceptClient(stale), "lower station revision");
        assertEquals(ObservationContextResolver.UpdateResult.STALE,
                ObservationContextResolver.removeClient(generationFour.stationId(), 3L),
                "stale removal");
        assertTrue(SpaceClientState.current().isPresent(), "stale removal retained state");
        assertEquals(ObservationContextResolver.UpdateResult.REMOVED,
                ObservationContextResolver.removeClient(generationFour.stationId(), 4L),
                "current removal");
        assertTrue(SpaceClientState.current().isEmpty(), "current removal cleared state");

        ObservationContext otherStation = context("other", 1L, new StationRegion(2, 0), MARS, 4L);
        ObservationContextResolver.acceptClient(otherStation);
        assertEquals(ObservationContextResolver.UpdateResult.IGNORED,
                ObservationContextResolver.removeClient(generationFour.stationId(), 99L),
                "different station removal");
        assertEquals(otherStation, SpaceClientState.current().orElseThrow(),
                "different station removal retained replacement");
        SpaceClientState.clear();
    }

    private static void packetRoundTripsAndRejectsInvalidStableIds() {
        ObservationJourney journey = new ObservationJourney(EARTH, MARS, StationJourneyPhase.CRUISE,
                100L, 200L);
        ObservationContext source = new ObservationContext(uuid("packet"), 12L,
                new StationRegion(-2, 3), EARTH, StationStatus.ACTIVE, Optional.of(journey), 44L);
        FriendlyByteBuf contextBytes = new FriendlyByteBuf(Unpooled.buffer());
        new StationContextPacket(source).encode(contextBytes);
        assertEquals(source, new StationContextPacket(contextBytes).observation(), "context packet round-trip");

        FriendlyByteBuf removedBytes = new FriendlyByteBuf(Unpooled.buffer());
        StationRemovedPacket removed = new StationRemovedPacket(source.stationId(), source.stationRevision());
        removed.encode(removedBytes);
        assertEquals(removed, new StationRemovedPacket(removedBytes), "removed packet round-trip");

        FriendlyByteBuf unknownStatus = prefix(source, 12L);
        unknownStatus.writeUtf("unknown", 32);
        unknownStatus.writeBoolean(false);
        unknownStatus.writeLong(44L);
        assertThrows(IllegalArgumentException.class, () -> new StationContextPacket(unknownStatus),
                "unknown status rejected");

        FriendlyByteBuf unknownPhase = prefix(source, 12L);
        unknownPhase.writeUtf(StationStatus.ACTIVE.id(), 32);
        unknownPhase.writeBoolean(true);
        unknownPhase.writeUtf(EARTH.toString(), 256);
        unknownPhase.writeUtf(MARS.toString(), 256);
        unknownPhase.writeUtf("not_a_phase", 32);
        unknownPhase.writeLong(0L);
        unknownPhase.writeLong(1L);
        unknownPhase.writeLong(44L);
        assertThrows(IllegalArgumentException.class, () -> new StationContextPacket(unknownPhase),
                "unknown phase rejected");

        FriendlyByteBuf negativeRevision = prefix(source, -1L);
        negativeRevision.writeUtf(StationStatus.ACTIVE.id(), 32);
        negativeRevision.writeBoolean(false);
        negativeRevision.writeLong(44L);
        assertThrows(IllegalArgumentException.class, () -> new StationContextPacket(negativeRevision),
                "negative revision rejected");

        FriendlyByteBuf oversizedId = new FriendlyByteBuf(Unpooled.buffer());
        oversizedId.writeUUID(source.stationId());
        oversizedId.writeLong(1L);
        oversizedId.writeInt(1);
        oversizedId.writeInt(0);
        oversizedId.writeUtf("wildfires:" + "x".repeat(300), 32767);
        assertThrows(RuntimeException.class, () -> new StationContextPacket(oversizedId),
                "oversized resource id rejected");
    }

    private static FriendlyByteBuf prefix(ObservationContext source, long revision) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUUID(source.stationId());
        buffer.writeLong(revision);
        buffer.writeInt(source.region().gridX());
        buffer.writeInt(source.region().gridZ());
        buffer.writeUtf(source.currentBody().toString(), 256);
        return buffer;
    }

    private static ObservationContext context(String seed, long revision, StationRegion region,
                                               ResourceLocation body, long generation) {
        return new ObservationContext(uuid(seed), revision, region, body, StationStatus.ACTIVE,
                Optional.empty(), generation);
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

    private static void assertEquals(Object expected, Object actual, String name) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
