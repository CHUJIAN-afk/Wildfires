package first.wildfires.network;

import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationJourney;
import first.wildfires.space.station.StationJourneyPhase;
import first.wildfires.space.station.StationRegion;
import first.wildfires.space.station.StationStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/** Strict bounded wire codec shared by the two station-observation packets. */
final class SpacePacketCodecs {

    private static final int MAX_RESOURCE_ID_LENGTH = 256;
    private static final int MAX_STABLE_ID_LENGTH = 32;

    private SpacePacketCodecs() {
    }

    static void writeContext(FriendlyByteBuf buffer, ObservationContext context) {
        buffer.writeUUID(context.stationId());
        buffer.writeLong(context.stationRevision());
        buffer.writeInt(context.region().gridX());
        buffer.writeInt(context.region().gridZ());
        writeResourceId(buffer, context.currentBody());
        buffer.writeUtf(context.status().id(), MAX_STABLE_ID_LENGTH);
        buffer.writeBoolean(context.journey().isPresent());
        context.journey().ifPresent(journey -> {
            writeResourceId(buffer, journey.fromBody());
            writeResourceId(buffer, journey.toBody());
            buffer.writeUtf(journey.phase().id(), MAX_STABLE_ID_LENGTH);
            buffer.writeLong(journey.phaseStartedGameTime());
            buffer.writeLong(journey.phaseDurationTicks());
        });
        buffer.writeLong(context.celestialRegistryGeneration());
    }

    static ObservationContext readContext(FriendlyByteBuf buffer) {
        UUID stationId = buffer.readUUID();
        long revision = requireNonNegative(buffer.readLong(), "station revision");
        StationRegion region = new StationRegion(buffer.readInt(), buffer.readInt());
        ResourceLocation currentBody = readResourceId(buffer);
        String statusId = buffer.readUtf(MAX_STABLE_ID_LENGTH);
        StationStatus status = StationStatus.fromId(statusId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown station status: " + statusId));
        Optional<ObservationJourney> journey = Optional.empty();
        if (buffer.readBoolean()) {
            ResourceLocation from = readResourceId(buffer);
            ResourceLocation to = readResourceId(buffer);
            String phaseId = buffer.readUtf(MAX_STABLE_ID_LENGTH);
            StationJourneyPhase phase = StationJourneyPhase.fromId(phaseId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown station journey phase: " + phaseId));
            long started = requireNonNegative(buffer.readLong(), "journey start");
            long duration = requireNonNegative(buffer.readLong(), "journey duration");
            journey = Optional.of(new ObservationJourney(from, to, phase, started, duration));
        }
        long generation = requireNonNegative(buffer.readLong(), "celestial registry generation");
        return new ObservationContext(stationId, revision, region, currentBody, status, journey, generation);
    }

    static void writeRemoved(FriendlyByteBuf buffer, UUID stationId, long revision) {
        buffer.writeUUID(stationId);
        buffer.writeLong(revision);
    }

    static Removed readRemoved(FriendlyByteBuf buffer) {
        return new Removed(buffer.readUUID(), requireNonNegative(buffer.readLong(), "removed revision"));
    }

    private static void writeResourceId(FriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeUtf(id.toString(), MAX_RESOURCE_ID_LENGTH);
    }

    private static ResourceLocation readResourceId(FriendlyByteBuf buffer) {
        String value = buffer.readUtf(MAX_RESOURCE_ID_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid synchronized resource id: " + value);
        }
        return id;
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    record Removed(UUID stationId, long revision) {
    }
}
