package first.wildfires.space.capsule;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persisted transaction identity and endpoint evidence for one capsule dimension transfer. */
public record ReturnCapsuleTransitionTicket(
        UUID ticketId,
        UUID stationId,
        Direction direction,
        ResourceLocation bodyId,
        ResourceLocation sourceDimension,
        BlockPos sourcePosition,
        ResourceLocation targetDimension,
        BlockPos targetPosition,
        UUID passengerId,
        long expectedCapsuleRevision,
        long createdGameTime,
        Stage stage) {

    private static final UUID LEGACY_UNKNOWN_PASSENGER = new UUID(0L, 0L);
    private static final ResourceLocation ORBIT = ResourceLocation.fromNamespaceAndPath("wildfires", "orbit");

    public ReturnCapsuleTransitionTicket {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(sourceDimension, "sourceDimension");
        sourcePosition = Objects.requireNonNull(sourcePosition, "sourcePosition").immutable();
        Objects.requireNonNull(targetDimension, "targetDimension");
        targetPosition = Objects.requireNonNull(targetPosition, "targetPosition").immutable();
        Objects.requireNonNull(passengerId, "passengerId");
        if (expectedCapsuleRevision < 0L || createdGameTime < 0L) {
            throw new IllegalArgumentException("Return capsule ticket revisions and times must be non-negative");
        }
        Objects.requireNonNull(stage, "stage");
    }

    public ReturnCapsuleTransitionTicket withStage(Stage stage) {
        return new ReturnCapsuleTransitionTicket(ticketId, stationId, direction, bodyId,
                sourceDimension, sourcePosition, targetDimension, targetPosition, passengerId,
                expectedCapsuleRevision, createdGameTime, stage);
    }

    public ResourceLocation surfaceDimension() {
        return direction == Direction.TO_STATION ? sourceDimension : targetDimension;
    }

    public BlockPos surfacePosition() {
        return direction == Direction.TO_STATION ? sourcePosition : targetPosition;
    }

    public boolean hasKnownPassenger() {
        return !LEGACY_UNKNOWN_PASSENGER.equals(passengerId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ticket_id", ticketId);
        tag.putUUID("station_id", stationId);
        tag.putString("direction", direction.id);
        tag.putString("body_id", bodyId.toString());
        putEndpoint(tag, "source", sourceDimension, sourcePosition);
        putEndpoint(tag, "target", targetDimension, targetPosition);
        tag.putUUID("passenger_id", passengerId);
        tag.putLong("expected_capsule_revision", expectedCapsuleRevision);
        tag.putLong("created_game_time", createdGameTime);
        tag.putString("stage", stage.id);
        return tag;
    }

    public static ReturnCapsuleTransitionTicket load(CompoundTag tag) {
        if (!tag.hasUUID("ticket_id") || !tag.hasUUID("station_id")
                || !tag.contains("direction", Tag.TAG_STRING)
                || !tag.contains("body_id", Tag.TAG_STRING)
                || !tag.contains("stage", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Incomplete return capsule transition ticket");
        }
        ResourceLocation body = requiredId(tag.getString("body_id"), "body_id");
        Direction direction = Direction.fromId(tag.getString("direction")).orElseThrow(
                () -> new IllegalArgumentException("Unknown capsule direction"));
        Stage stage = Stage.fromId(tag.getString("stage")).orElseThrow(
                () -> new IllegalArgumentException("Unknown capsule ticket stage"));
        if (hasEndpoint(tag, "source") && hasEndpoint(tag, "target")
                && tag.hasUUID("passenger_id")
                && tag.contains("expected_capsule_revision", Tag.TAG_LONG)
                && tag.contains("created_game_time", Tag.TAG_LONG)) {
            Endpoint source = loadEndpoint(tag, "source");
            Endpoint target = loadEndpoint(tag, "target");
            return new ReturnCapsuleTransitionTicket(tag.getUUID("ticket_id"), tag.getUUID("station_id"),
                    direction, body, source.dimension(), source.position(), target.dimension(), target.position(),
                    tag.getUUID("passenger_id"), tag.getLong("expected_capsule_revision"),
                    tag.getLong("created_game_time"), stage);
        }
        // Compatibility with P7 development worlds written before endpoint evidence was expanded.
        if (!tag.contains("surface_dimension", Tag.TAG_STRING)
                || !tag.contains("surface_x", Tag.TAG_INT)
                || !tag.contains("surface_y", Tag.TAG_INT)
                || !tag.contains("surface_z", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Incomplete return capsule transition endpoints");
        }
        ResourceLocation surfaceDimension = requiredId(tag.getString("surface_dimension"), "surface_dimension");
        BlockPos surfacePosition = new BlockPos(tag.getInt("surface_x"), tag.getInt("surface_y"),
                tag.getInt("surface_z"));
        ResourceLocation sourceDimension = direction == Direction.TO_STATION ? surfaceDimension : ORBIT;
        ResourceLocation targetDimension = direction == Direction.TO_STATION ? ORBIT : surfaceDimension;
        BlockPos sourcePosition = direction == Direction.TO_STATION ? surfacePosition : BlockPos.ZERO;
        BlockPos targetPosition = direction == Direction.TO_STATION ? BlockPos.ZERO : surfacePosition;
        return new ReturnCapsuleTransitionTicket(tag.getUUID("ticket_id"), tag.getUUID("station_id"),
                direction, body, sourceDimension, sourcePosition, targetDimension, targetPosition,
                LEGACY_UNKNOWN_PASSENGER, 0L, 0L, stage);
    }

    private static void putEndpoint(CompoundTag tag, String prefix, ResourceLocation dimension,
                                    BlockPos position) {
        tag.putString(prefix + "_dimension", dimension.toString());
        tag.putInt(prefix + "_x", position.getX());
        tag.putInt(prefix + "_y", position.getY());
        tag.putInt(prefix + "_z", position.getZ());
    }

    private static boolean hasEndpoint(CompoundTag tag, String prefix) {
        return tag.contains(prefix + "_dimension", Tag.TAG_STRING)
                && tag.contains(prefix + "_x", Tag.TAG_INT)
                && tag.contains(prefix + "_y", Tag.TAG_INT)
                && tag.contains(prefix + "_z", Tag.TAG_INT);
    }

    private static Endpoint loadEndpoint(CompoundTag tag, String prefix) {
        return new Endpoint(requiredId(tag.getString(prefix + "_dimension"), prefix + "_dimension"),
                new BlockPos(tag.getInt(prefix + "_x"), tag.getInt(prefix + "_y"),
                        tag.getInt(prefix + "_z")));
    }

    private static ResourceLocation requiredId(String value, String name) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException("Invalid " + name + ": " + value);
        return id;
    }

    public enum Direction {
        TO_STATION("to_station"), TO_SURFACE("to_surface");
        private final String id;
        Direction(String id) { this.id = id; }
        public static Optional<Direction> fromId(String id) {
            for (Direction value : values()) if (value.id.equals(id)) return Optional.of(value);
            return Optional.empty();
        }
    }

    public enum Stage {
        PREPARED("prepared"), TRANSFERRED("transferred"), COMMITTED("committed"),
        ROLLED_BACK("rolled_back"), RECOVERY("recovery");
        private final String id;
        Stage(String id) { this.id = id; }
        public static Optional<Stage> fromId(String id) {
            for (Stage value : values()) if (value.id.equals(id)) return Optional.of(value);
            return Optional.empty();
        }
    }

    private record Endpoint(ResourceLocation dimension, BlockPos position) {
    }
}
