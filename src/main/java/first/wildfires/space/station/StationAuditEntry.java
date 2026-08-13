package first.wildfires.space.station;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded persisted audit event for authoritative station mutations. */
public record StationAuditEntry(long sequence, long gameTime, UUID stationId,
                                Optional<UUID> actor, Action action, String detail) {

    public StationAuditEntry {
        if (sequence < 0L || gameTime < 0L) {
            throw new IllegalArgumentException("Station audit sequence and gameTime must be non-negative");
        }
        Objects.requireNonNull(stationId, "stationId");
        actor = Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        detail = Objects.requireNonNull(detail, "detail");
        if (detail.length() > SpaceConstants.MAX_AUDIT_DETAIL_LENGTH) {
            throw new IllegalArgumentException("Station audit detail exceeds "
                    + SpaceConstants.MAX_AUDIT_DETAIL_LENGTH + " characters");
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("sequence", sequence);
        tag.putLong("game_time", gameTime);
        tag.putUUID("station_id", stationId);
        actor.ifPresent(value -> tag.putUUID("actor", value));
        tag.putString("action", action.id());
        tag.putString("detail", detail);
        return tag;
    }

    public static StationAuditEntry load(CompoundTag tag) {
        requireType(tag, "sequence", net.minecraft.nbt.Tag.TAG_LONG);
        requireType(tag, "game_time", net.minecraft.nbt.Tag.TAG_LONG);
        requireType(tag, "action", net.minecraft.nbt.Tag.TAG_STRING);
        requireType(tag, "detail", net.minecraft.nbt.Tag.TAG_STRING);
        if (tag.contains("actor") && !tag.hasUUID("actor")) {
            throw new IllegalArgumentException("Invalid station audit actor UUID");
        }
        Action action = Action.fromId(tag.getString("action"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown station audit action: " + tag.getString("action")));
        return new StationAuditEntry(tag.getLong("sequence"), tag.getLong("game_time"),
                requiredUuid(tag, "station_id"), tag.hasUUID("actor")
                ? Optional.of(tag.getUUID("actor")) : Optional.empty(), action, tag.getString("detail"));
    }

    private static UUID requiredUuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException("Missing station audit UUID: " + key);
        }
        return tag.getUUID(key);
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw new IllegalArgumentException("Missing or invalid station audit field: " + key);
        }
    }

    public enum Action {
        CREATED("created"),
        RENAMED("renamed"),
        MEMBER_CHANGED("member_changed"),
        JOURNEY_CHANGED("journey_changed"),
        REMOVED("removed"),
        DEFINITIONS_RECONCILED("definitions_reconciled"),
        RECOVERED("recovered"),
        RETURN_CAPSULE_CHANGED("return_capsule_changed");

        private static final Map<String, Action> BY_ID;

        static {
            Map<String, Action> actions = new LinkedHashMap<>();
            for (Action action : values()) {
                actions.put(action.id, action);
            }
            BY_ID = Map.copyOf(actions);
        }

        private final String id;

        Action(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<Action> fromId(String id) {
            return Optional.ofNullable(BY_ID.get(id));
        }
    }
}
