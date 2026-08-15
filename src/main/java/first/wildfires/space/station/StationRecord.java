package first.wildfires.space.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable authoritative station record persisted by {@link SpaceSavedData}. */
public record StationRecord(
        UUID stationId,
        String name,
        UUID owner,
        Map<UUID, StationPermission> members,
        StationRegion region,
        ResourceLocation currentBody,
        Optional<StationJourney> journey,
        StationStatus status,
        ResourceLocation primaryDockId,
        Map<ResourceLocation, StationDockRecord> docks,
        Set<UUID> ownedReturnCapsules,
        Map<ResourceLocation, ResourceLocation> landingTargets,
        long revision,
        long createdGameTime,
        long modifiedGameTime) {

    public static final int RECORD_VERSION = 1;
    private static final List<String> JOURNEY_KEYS = List.of(
            "schema_version", "journey_id", "route_id", "from_body", "to_body", "mode", "phase",
            "phase_started_game_time", "phase_duration_ticks", "requested_by");

    public StationRecord {
        Objects.requireNonNull(stationId, "stationId");
        name = validateName(name);
        Objects.requireNonNull(owner, "owner");
        members = immutableMembers(members, owner);
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(currentBody, "currentBody");
        journey = Objects.requireNonNull(journey, "journey");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(primaryDockId, "primaryDockId");
        docks = immutableDocks(docks, region);
        ownedReturnCapsules = immutableUuidSet(ownedReturnCapsules,
                SpaceConstants.MAX_RETURN_CAPSULES, "ownedReturnCapsules");
        landingTargets = immutableLandingTargets(landingTargets);
        if (!docks.containsKey(primaryDockId)) {
            throw new IllegalArgumentException("Primary station dock is not present: " + primaryDockId);
        }
        if (revision < 0L || createdGameTime < 0L || modifiedGameTime < createdGameTime) {
            throw new IllegalArgumentException("Invalid station revision or gameTime range");
        }
        new StationJourneyService.State(currentBody, journey, revision);
        if (status == StationStatus.ACTIVE
                && journey.filter(value -> value.phase() == StationJourneyPhase.FAULTED).isPresent()) {
            throw new IllegalArgumentException("An active station cannot carry a faulted journey");
        }
    }

    public static StationRecord create(UUID stationId, String name, UUID owner, StationRegion region,
                                       ResourceLocation currentBody, long gameTime) {
        ResourceLocation primaryDock = ResourceLocation.fromNamespaceAndPath("wildfires", "primary");
        StationDockRecord dock = new StationDockRecord(primaryDock, region.safePoint());
        return new StationRecord(stationId, name, owner, Map.of(), region, currentBody,
                Optional.empty(), StationStatus.ACTIVE, primaryDock, Map.of(primaryDock, dock),
                Set.of(), Map.of(), 1L, gameTime, gameTime);
    }

    public boolean mayView(UUID actor) {
        return owner.equals(actor) || members.containsKey(actor);
    }

    public boolean mayOperate(UUID actor) {
        return owner.equals(actor) || Optional.ofNullable(members.get(actor))
                .map(StationPermission::mayOperate).orElse(false);
    }

    public boolean mayManage(UUID actor) {
        return owner.equals(actor) || Optional.ofNullable(members.get(actor))
                .map(StationPermission::mayManage).orElse(false);
    }

    public StationDockRecord primaryDock() {
        return docks.get(primaryDockId);
    }

    public StationRecord renamed(String newName, long gameTime) {
        String validated = validateName(newName);
        if (name.equals(validated)) {
            return this;
        }
        return copy(validated, members, currentBody, journey, status, docks, ownedReturnCapsules,
                landingTargets, nextRevision(), gameTime);
    }

    public StationRecord withMember(UUID member, Optional<StationPermission> permission, long gameTime) {
        Objects.requireNonNull(member, "member");
        permission = Objects.requireNonNull(permission, "permission");
        if (owner.equals(member)) {
            throw new IllegalArgumentException("Station owner cannot also be stored as a member");
        }
        Map<UUID, StationPermission> updated = new LinkedHashMap<>(members);
        StationPermission previous = updated.get(member);
        if (Objects.equals(previous, permission.orElse(null))) {
            return this;
        }
        permission.ifPresentOrElse(value -> updated.put(member, value), () -> updated.remove(member));
        return copy(name, updated, currentBody, journey, status, docks, ownedReturnCapsules,
                landingTargets, nextRevision(), gameTime);
    }

    public StationRecord withJourneyState(StationJourneyService.State state, StationStatus newStatus,
                                          long gameTime) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(newStatus, "newStatus");
        if (currentBody.equals(state.currentBody()) && journey.equals(state.journey())
                && status == newStatus && revision == state.revision()) {
            return this;
        }
        if (state.revision() <= revision) {
            throw new IllegalArgumentException("Changed journey state must advance station revision");
        }
        return copy(name, members, state.currentBody(), state.journey(), newStatus, docks,
                ownedReturnCapsules, landingTargets, state.revision(), gameTime);
    }

    public StationRecord withHealth(StationStatus newStatus, Optional<StationJourney> newJourney,
                                    long gameTime) {
        Objects.requireNonNull(newStatus, "newStatus");
        newJourney = Objects.requireNonNull(newJourney, "newJourney");
        if (status == newStatus && journey.equals(newJourney)) {
            return this;
        }
        return copy(name, members, currentBody, newJourney, newStatus, docks, ownedReturnCapsules,
                landingTargets, nextRevision(), gameTime);
    }

    public StationRecord withReturnCapsule(UUID capsuleId, boolean owned, long gameTime) {
        Objects.requireNonNull(capsuleId, "capsuleId");
        Set<UUID> updated = new LinkedHashSet<>(ownedReturnCapsules);
        boolean changed = owned ? updated.add(capsuleId) : updated.remove(capsuleId);
        if (!changed) {
            return this;
        }
        return copy(name, members, currentBody, journey, status, docks, updated,
                landingTargets, nextRevision(), gameTime);
    }

    public StationRecord withDock(StationDockRecord dock, boolean present, long gameTime) {
        Objects.requireNonNull(dock, "dock");
        if (dock.id().equals(primaryDockId) && !present) {
            throw new IllegalArgumentException("The primary station dock cannot be removed");
        }
        Map<ResourceLocation, StationDockRecord> updated = new LinkedHashMap<>(docks);
        boolean changed;
        if (present) {
            StationDockRecord previous = updated.putIfAbsent(dock.id(), dock);
            changed = previous == null;
            if (previous != null && !previous.equals(dock)) {
                throw new IllegalArgumentException("Station dock id already exists at another position");
            }
            if (updated.values().stream().filter(value -> value.position().equals(dock.position())).count() > 1L) {
                throw new IllegalArgumentException("Station dock position is already registered");
            }
        } else {
            changed = updated.remove(dock.id()) != null;
        }
        if (!changed) return this;
        return copy(name, members, currentBody, journey, status, updated, ownedReturnCapsules,
                landingTargets, nextRevision(), gameTime);
    }

    private StationRecord copy(String newName, Map<UUID, StationPermission> newMembers,
                               ResourceLocation newCurrentBody, Optional<StationJourney> newJourney,
                               StationStatus newStatus, Map<ResourceLocation, StationDockRecord> newDocks,
                               Set<UUID> newCapsules,
                               Map<ResourceLocation, ResourceLocation> newLandingTargets,
                               long newRevision, long gameTime) {
        return new StationRecord(stationId, newName, owner, newMembers, region, newCurrentBody,
                newJourney, newStatus, primaryDockId, newDocks, newCapsules, newLandingTargets,
                newRevision, createdGameTime, Math.max(modifiedGameTime, requireGameTime(gameTime)));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("record_version", RECORD_VERSION);
        tag.putUUID("station_id", stationId);
        tag.putString("name", name);
        tag.putUUID("owner", owner);
        tag.put("members", saveMembers());
        tag.put("region", region.save());
        tag.putString("current_body", currentBody.toString());
        journey.ifPresent(value -> tag.put("journey", saveJourney(value)));
        tag.putString("status", status.id());
        tag.putString("primary_dock", primaryDockId.toString());
        tag.put("docks", saveDocks());
        tag.put("owned_return_capsules", saveUuids(ownedReturnCapsules));
        tag.put("landing_targets", saveLandingTargets());
        tag.putLong("revision", revision);
        tag.putLong("created_game_time", createdGameTime);
        tag.putLong("modified_game_time", modifiedGameTime);
        return tag;
    }

    public static StationRecord load(CompoundTag tag) {
        int version = requiredInt(tag, "record_version");
        if (version != RECORD_VERSION) {
            throw new IllegalArgumentException("Unsupported station record version: " + version);
        }
        UUID stationId = requiredUuid(tag, "station_id");
        UUID owner = requiredUuid(tag, "owner");
        Map<UUID, StationPermission> members = loadMembers(requiredCompoundList(tag, "members"));
        StationRegion region = StationRegion.load(requiredCompound(tag, "region"));
        ResourceLocation currentBody = requiredId(requiredString(tag, "current_body"), "current_body");
        if (tag.contains("journey") && !tag.contains("journey", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Station journey has the wrong NBT type");
        }
        Optional<StationJourney> journey = tag.contains("journey", Tag.TAG_COMPOUND)
                ? Optional.of(loadJourney(tag.getCompound("journey"))) : Optional.empty();
        StationStatus status = StationStatus.fromId(requiredString(tag, "status"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown station status: " + tag.getString("status")));
        ResourceLocation primaryDock = requiredId(requiredString(tag, "primary_dock"), "primary_dock");
        Map<ResourceLocation, StationDockRecord> docks = loadDocks(requiredCompoundList(tag, "docks"));
        Set<UUID> capsules = loadUuids(requiredCompoundList(tag, "owned_return_capsules"));
        Map<ResourceLocation, ResourceLocation> landingTargets = loadLandingTargets(
                requiredCompoundList(tag, "landing_targets"));
        return new StationRecord(stationId, requiredString(tag, "name"), owner, members, region, currentBody,
                journey, status, primaryDock, docks, capsules, landingTargets, requiredLong(tag, "revision"),
                requiredLong(tag, "created_game_time"), requiredLong(tag, "modified_game_time"));
    }

    private ListTag saveMembers() {
        ListTag list = new ListTag();
        members.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> {
                    CompoundTag member = new CompoundTag();
                    member.putUUID("uuid", entry.getKey());
                    member.putString("permission", entry.getValue().id());
                    list.add(member);
                });
        return list;
    }

    private ListTag saveDocks() {
        ListTag list = new ListTag();
        docks.values().stream().sorted(Comparator.comparing(value -> value.id().toString()))
                .map(StationDockRecord::save).forEach(list::add);
        return list;
    }

    private ListTag saveLandingTargets() {
        ListTag list = new ListTag();
        landingTargets.entrySet().stream().sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    CompoundTag target = new CompoundTag();
                    target.putString("body", entry.getKey().toString());
                    target.putString("target", entry.getValue().toString());
                    list.add(target);
                });
        return list;
    }

    private static CompoundTag saveJourney(StationJourney journey) {
        CompoundTag tag = new CompoundTag();
        journey.toSnapshot().forEach(tag::putString);
        return tag;
    }

    private static StationJourney loadJourney(CompoundTag tag) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String key : JOURNEY_KEYS) {
            if (tag.contains(key, Tag.TAG_STRING)) {
                snapshot.put(key, tag.getString(key));
            }
        }
        return StationJourney.fromSnapshot(snapshot);
    }

    private static ListTag saveUuids(Set<UUID> values) {
        ListTag list = new ListTag();
        values.stream().sorted(Comparator.comparing(UUID::toString)).forEach(value -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", value);
            list.add(entry);
        });
        return list;
    }

    private static Map<UUID, StationPermission> loadMembers(ListTag list) {
        requireListLimit(list, SpaceConstants.MAX_MEMBERS, "members");
        Map<UUID, StationPermission> members = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            UUID uuid = requiredUuid(entry, "uuid");
            StationPermission permission = StationPermission.fromId(entry.getString("permission"))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown station permission: " + entry.getString("permission")));
            if (members.put(uuid, permission) != null) {
                throw new IllegalArgumentException("Duplicate station member UUID: " + uuid);
            }
        }
        return members;
    }

    private static Map<ResourceLocation, StationDockRecord> loadDocks(ListTag list) {
        requireListLimit(list, SpaceConstants.MAX_DOCKS, "docks");
        Map<ResourceLocation, StationDockRecord> docks = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            StationDockRecord dock = StationDockRecord.load(list.getCompound(index));
            if (docks.put(dock.id(), dock) != null) {
                throw new IllegalArgumentException("Duplicate station dock id: " + dock.id());
            }
        }
        return docks;
    }

    private static Set<UUID> loadUuids(ListTag list) {
        requireListLimit(list, SpaceConstants.MAX_RETURN_CAPSULES, "owned_return_capsules");
        Set<UUID> values = new LinkedHashSet<>();
        for (int index = 0; index < list.size(); index++) {
            UUID uuid = requiredUuid(list.getCompound(index), "uuid");
            if (!values.add(uuid)) {
                throw new IllegalArgumentException("Duplicate return capsule UUID: " + uuid);
            }
        }
        return values;
    }

    private static Map<ResourceLocation, ResourceLocation> loadLandingTargets(ListTag list) {
        requireListLimit(list, SpaceConstants.MAX_LANDING_TARGETS, "landing_targets");
        Map<ResourceLocation, ResourceLocation> targets = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            ResourceLocation body = requiredId(entry.getString("body"), "landing target body");
            ResourceLocation target = requiredId(entry.getString("target"), "landing target id");
            if (targets.put(body, target) != null) {
                throw new IllegalArgumentException("Duplicate landing target body: " + body);
            }
        }
        return targets;
    }

    private static Map<UUID, StationPermission> immutableMembers(Map<UUID, StationPermission> source,
                                                                  UUID owner) {
        Objects.requireNonNull(source, "members");
        if (source.size() > SpaceConstants.MAX_MEMBERS) {
            throw new IllegalArgumentException("Station member limit exceeded: " + source.size());
        }
        Map<UUID, StationPermission> copy = new LinkedHashMap<>();
        source.forEach((uuid, permission) -> {
            Objects.requireNonNull(uuid, "member UUID");
            Objects.requireNonNull(permission, "member permission");
            if (owner.equals(uuid)) {
                throw new IllegalArgumentException("Station owner cannot also be stored as a member");
            }
            copy.put(uuid, permission);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<ResourceLocation, StationDockRecord> immutableDocks(
            Map<ResourceLocation, StationDockRecord> source, StationRegion region) {
        Objects.requireNonNull(source, "docks");
        if (source.isEmpty() || source.size() > SpaceConstants.MAX_DOCKS) {
            throw new IllegalArgumentException("Station must have 1.." + SpaceConstants.MAX_DOCKS + " docks");
        }
        Map<ResourceLocation, StationDockRecord> copy = new LinkedHashMap<>();
        source.forEach((id, dock) -> {
            Objects.requireNonNull(id, "dock id");
            Objects.requireNonNull(dock, "dock");
            if (!id.equals(dock.id())) {
                throw new IllegalArgumentException("Station dock map key does not match record id: " + id);
            }
            if (!region.containsBuildArea(dock.position())) {
                throw new IllegalArgumentException("Station dock is outside build radius: " + dock.position());
            }
            copy.put(id, dock);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Set<UUID> immutableUuidSet(Set<UUID> source, int maximum, String name) {
        Objects.requireNonNull(source, name);
        if (source.size() > maximum) {
            throw new IllegalArgumentException(name + " limit exceeded: " + source.size());
        }
        Set<UUID> copy = new LinkedHashSet<>();
        source.forEach(value -> copy.add(Objects.requireNonNull(value, name + " entry")));
        return Collections.unmodifiableSet(copy);
    }

    private static Map<ResourceLocation, ResourceLocation> immutableLandingTargets(
            Map<ResourceLocation, ResourceLocation> source) {
        Objects.requireNonNull(source, "landingTargets");
        if (source.size() > SpaceConstants.MAX_LANDING_TARGETS) {
            throw new IllegalArgumentException("Landing target limit exceeded: " + source.size());
        }
        Map<ResourceLocation, ResourceLocation> copy = new LinkedHashMap<>();
        source.forEach((body, target) -> copy.put(Objects.requireNonNull(body, "landing body"),
                Objects.requireNonNull(target, "landing target")));
        return Collections.unmodifiableMap(copy);
    }

    private static String validateName(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank() || !value.equals(value.strip())
                || value.length() > SpaceConstants.MAX_STATION_NAME_LENGTH) {
            throw new IllegalArgumentException("Station name must be trimmed and contain 1.."
                    + SpaceConstants.MAX_STATION_NAME_LENGTH + " characters");
        }
        return value;
    }

    private long nextRevision() {
        try {
            return Math.incrementExact(revision);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Station revision overflow", exception);
        }
    }

    private static long requireGameTime(long gameTime) {
        if (gameTime < 0L) {
            throw new IllegalArgumentException("Station mutation gameTime must be non-negative");
        }
        return gameTime;
    }

    private static UUID requiredUuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) {
            throw new IllegalArgumentException("Missing station UUID: " + key);
        }
        return tag.getUUID(key);
    }

    private static ResourceLocation requiredId(String value, String field) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid resource id in " + field + ": " + value);
        }
        return id;
    }

    private static int requiredInt(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Missing or invalid station integer: " + key);
        }
        return tag.getInt(key);
    }

    private static long requiredLong(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing or invalid station long: " + key);
        }
        return tag.getLong(key);
    }

    private static String requiredString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing or invalid station string: " + key);
        }
        return tag.getString(key);
    }

    private static CompoundTag requiredCompound(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing or invalid station compound: " + key);
        }
        return tag.getCompound(key);
    }

    private static ListTag requiredCompoundList(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag list)) {
            throw new IllegalArgumentException("Missing or invalid station list: " + key);
        }
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag)) {
                throw new IllegalArgumentException("Station list is not compound-valued: " + key);
            }
        }
        return list;
    }

    private static void requireListLimit(ListTag list, int maximum, String name) {
        if (list.size() > maximum || list.size() > SpaceConstants.MAX_STATION_RECORD_NBT_LIST) {
            throw new IllegalArgumentException("Station " + name + " list exceeds limit: " + list.size());
        }
    }
}
