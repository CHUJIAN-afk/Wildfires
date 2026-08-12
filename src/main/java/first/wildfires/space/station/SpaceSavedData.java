package first.wildfires.space.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

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

/** Global station authority stored only in the server overworld DataStorage. */
public final class SpaceSavedData extends SavedData {

    public static final int DATA_VERSION = 1;
    public static final String FILE_ID = "wildfires_space";

    private final Map<UUID, StationRecord> stations;
    private final Map<StationRegion, UUID> stationByRegion;
    private final Set<StationRegion> retiredRegions;
    private final List<StationAuditEntry> auditEntries;
    private long nextRegionOrdinal;
    private long nextAuditSequence;
    private final Optional<String> writeBlockReason;
    private final CompoundTag preservedBlockedData;

    public SpaceSavedData() {
        this(new LinkedHashMap<>(), new LinkedHashSet<>(), new ArrayList<>(), 1L, 0L,
                Optional.empty(), null);
    }

    private SpaceSavedData(Map<UUID, StationRecord> stations,
                           Set<StationRegion> retiredRegions,
                           List<StationAuditEntry> auditEntries,
                           long nextRegionOrdinal,
                           long nextAuditSequence,
                           Optional<String> writeBlockReason,
                           CompoundTag preservedBlockedData) {
        this.stations = new LinkedHashMap<>(stations);
        this.stationByRegion = new LinkedHashMap<>();
        this.stations.forEach((id, station) -> {
            UUID previous = this.stationByRegion.put(station.region(), id);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate station region " + station.region()
                        + " for " + previous + " and " + id);
            }
            if (station.region().reserved()) {
                throw new IllegalArgumentException("Ordinary station uses reserved region: " + id);
            }
        });
        this.retiredRegions = new LinkedHashSet<>(retiredRegions);
        if (!Collections.disjoint(this.stationByRegion.keySet(), this.retiredRegions)) {
            throw new IllegalArgumentException("Active and retired station regions overlap");
        }
        this.auditEntries = new ArrayList<>(auditEntries);
        if (nextRegionOrdinal < 1L || nextAuditSequence < 0L) {
            throw new IllegalArgumentException("Invalid next station region or audit sequence");
        }
        this.nextRegionOrdinal = nextRegionOrdinal;
        this.nextAuditSequence = nextAuditSequence;
        this.writeBlockReason = Objects.requireNonNull(writeBlockReason, "writeBlockReason");
        this.preservedBlockedData = preservedBlockedData == null ? null : preservedBlockedData.copy();
        validateLimits();
    }

    public static SpaceSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                SpaceSavedData::load, SpaceSavedData::new, FILE_ID);
    }

    public static SpaceSavedData load(CompoundTag source) {
        Objects.requireNonNull(source, "source");
        int sourceVersion = source.contains("data_version", Tag.TAG_INT)
                ? source.getInt("data_version") : 0;
        if (sourceVersion > DATA_VERSION) {
            return blocked(source, "Space data version " + sourceVersion
                    + " is newer than supported version " + DATA_VERSION);
        }
        if (sourceVersion < 0) {
            return blocked(source, "Space data version is negative: " + sourceVersion);
        }
        try {
            CompoundTag tag = SpaceDataMigrator.migrate(source, sourceVersion);
            ListTag stationTags = requiredCompoundList(tag, "stations");
            requireLimit(stationTags.size(), SpaceConstants.MAX_STATIONS, "stations");
            Map<UUID, StationRecord> stations = new LinkedHashMap<>();
            for (int index = 0; index < stationTags.size(); index++) {
                StationRecord station = StationRecord.load(stationTags.getCompound(index));
                if (stations.put(station.stationId(), station) != null) {
                    throw new IllegalArgumentException("Duplicate station UUID: " + station.stationId());
                }
            }

            ListTag retiredTags = requiredCompoundList(tag, "retired_regions");
            requireLimit(retiredTags.size(), SpaceConstants.MAX_RETIRED_REGIONS, "retired_regions");
            Set<StationRegion> retired = new LinkedHashSet<>();
            for (int index = 0; index < retiredTags.size(); index++) {
                StationRegion region = StationRegion.load(retiredTags.getCompound(index));
                if (region.reserved() || !retired.add(region)) {
                    throw new IllegalArgumentException("Invalid or duplicate retired station region: " + region);
                }
            }

            ListTag auditTags = requiredCompoundList(tag, "audit");
            requireLimit(auditTags.size(), SpaceConstants.MAX_AUDIT_ENTRIES, "audit");
            List<StationAuditEntry> audit = new ArrayList<>();
            long previousSequence = -1L;
            for (int index = 0; index < auditTags.size(); index++) {
                StationAuditEntry entry = StationAuditEntry.load(auditTags.getCompound(index));
                if (entry.sequence() <= previousSequence) {
                    throw new IllegalArgumentException("Station audit sequence is not strictly increasing");
                }
                previousSequence = entry.sequence();
                audit.add(entry);
            }
            long nextAudit = requiredLong(tag, "next_audit_sequence");
            if (previousSequence >= nextAudit) {
                throw new IllegalArgumentException("Next station audit sequence does not follow saved audit");
            }
            return new SpaceSavedData(stations, retired, audit,
                    requiredLong(tag, "next_region_ordinal"), nextAudit, Optional.empty(), null);
        } catch (RuntimeException exception) {
            return blocked(source, "Space data is invalid and was opened read-only: " + exception.getMessage());
        }
    }

    private static SpaceSavedData blocked(CompoundTag source, String reason) {
        return new SpaceSavedData(Map.of(), Set.of(), List.of(), 1L, 0L,
                Optional.of(reason), source);
    }

    public boolean writable() {
        return writeBlockReason.isEmpty();
    }

    public Optional<String> writeBlockReason() {
        return writeBlockReason;
    }

    public Map<UUID, StationRecord> stations() {
        return Collections.unmodifiableMap(stations);
    }

    public Optional<StationRecord> station(UUID id) {
        return Optional.ofNullable(stations.get(Objects.requireNonNull(id, "id")));
    }

    public Optional<StationRecord> stationAt(int blockX, int blockZ) {
        return StationRegion.fromBlock(blockX, blockZ)
                .map(stationByRegion::get)
                .map(stations::get);
    }

    public Set<StationRegion> occupiedRegions() {
        return Collections.unmodifiableSet(stationByRegion.keySet());
    }

    public Set<StationRegion> retiredRegions() {
        return Collections.unmodifiableSet(retiredRegions);
    }

    public List<StationAuditEntry> auditEntries() {
        return List.copyOf(auditEntries);
    }

    public long nextRegionOrdinal() {
        return nextRegionOrdinal;
    }

    void createStation(StationRecord station, long followingRegionOrdinal,
                       Optional<UUID> actor, long gameTime) {
        ensureWritable();
        Objects.requireNonNull(station, "station");
        if (stations.size() >= SpaceConstants.MAX_STATIONS) {
            throw new IllegalStateException("Station limit reached: " + SpaceConstants.MAX_STATIONS);
        }
        if (stations.containsKey(station.stationId())) {
            throw new IllegalArgumentException("Duplicate station UUID: " + station.stationId());
        }
        if (stationByRegion.containsKey(station.region()) || retiredRegions.contains(station.region())) {
            throw new IllegalArgumentException("Station region is unavailable: " + station.region());
        }
        StationAuditEntry audit = prepareAudit(station.stationId(), actor,
                StationAuditEntry.Action.CREATED, gameTime, station.name());
        stations.put(station.stationId(), station);
        stationByRegion.put(station.region(), station.stationId());
        nextRegionOrdinal = followingRegionOrdinal;
        appendAudit(audit);
        setDirty();
    }

    void replaceStation(StationRecord station, Optional<UUID> actor,
                        StationAuditEntry.Action action, String detail, long gameTime) {
        ensureWritable();
        Objects.requireNonNull(station, "station");
        StationRecord previous = stations.get(station.stationId());
        if (previous == null) {
            throw new IllegalArgumentException("Unknown station UUID: " + station.stationId());
        }
        if (!previous.region().equals(station.region())) {
            throw new IllegalArgumentException("Station service cannot move an allocated region");
        }
        if (station.revision() <= previous.revision()) {
            throw new IllegalArgumentException("Station replacement must advance revision");
        }
        StationAuditEntry audit = prepareAudit(station.stationId(), actor, action, gameTime, detail);
        stations.put(station.stationId(), station);
        appendAudit(audit);
        setDirty();
    }

    void removeStation(UUID stationId, Optional<UUID> actor, long gameTime) {
        ensureWritable();
        Objects.requireNonNull(stationId, "stationId");
        StationRecord removed = stations.get(stationId);
        if (removed == null) {
            throw new IllegalArgumentException("Unknown station UUID: " + stationId);
        }
        if (retiredRegions.size() >= SpaceConstants.MAX_RETIRED_REGIONS) {
            throw new IllegalStateException("Retired station region limit reached");
        }
        StationAuditEntry audit = prepareAudit(stationId, actor, StationAuditEntry.Action.REMOVED,
                gameTime, removed.name());
        stations.remove(stationId);
        stationByRegion.remove(removed.region());
        retiredRegions.add(removed.region());
        appendAudit(audit);
        setDirty();
    }

    private StationAuditEntry prepareAudit(UUID stationId, Optional<UUID> actor,
                                           StationAuditEntry.Action action,
                                           long gameTime, String detail) {
        if (nextAuditSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Station audit sequence overflow");
        }
        return new StationAuditEntry(nextAuditSequence, gameTime, stationId, actor, action, detail);
    }

    private void appendAudit(StationAuditEntry entry) {
        nextAuditSequence = entry.sequence() + 1L;
        if (auditEntries.size() == SpaceConstants.MAX_AUDIT_ENTRIES) {
            auditEntries.remove(0);
        }
        auditEntries.add(entry);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ensureWritable();
        tag.putInt("data_version", DATA_VERSION);
        ListTag stationTags = new ListTag();
        stations.values().stream().sorted(Comparator.comparing(value -> value.stationId().toString()))
                .map(StationRecord::save).forEach(stationTags::add);
        tag.put("stations", stationTags);
        ListTag retiredTags = new ListTag();
        retiredRegions.stream().sorted(Comparator.comparingInt(StationRegion::gridX)
                        .thenComparingInt(StationRegion::gridZ))
                .map(StationRegion::save).forEach(retiredTags::add);
        tag.put("retired_regions", retiredTags);
        ListTag auditTags = new ListTag();
        auditEntries.forEach(entry -> auditTags.add(entry.save()));
        tag.put("audit", auditTags);
        tag.putLong("next_region_ordinal", nextRegionOrdinal);
        tag.putLong("next_audit_sequence", nextAuditSequence);
        return tag;
    }

    @Override
    public void setDirty() {
        ensureWritable();
        super.setDirty();
    }

    @Override
    public void setDirty(boolean dirty) {
        if (dirty) {
            ensureWritable();
        }
        super.setDirty(dirty);
    }

    private void ensureWritable() {
        if (!writable()) {
            throw new IllegalStateException(writeBlockReason.orElse("Space data is read-only"));
        }
    }

    private void validateLimits() {
        requireLimit(stations.size(), SpaceConstants.MAX_STATIONS, "stations");
        requireLimit(retiredRegions.size(), SpaceConstants.MAX_RETIRED_REGIONS, "retired_regions");
        requireLimit(auditEntries.size(), SpaceConstants.MAX_AUDIT_ENTRIES, "audit");
    }

    private static void requireLimit(int size, int maximum, String name) {
        if (size > maximum) {
            throw new IllegalArgumentException("Space SavedData " + name + " exceeds limit: " + size);
        }
    }

    private static ListTag requiredCompoundList(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag list)) {
            throw new IllegalArgumentException("Missing or invalid space SavedData list: " + key);
        }
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag)) {
                throw new IllegalArgumentException("Space SavedData list is not compound-valued: " + key);
            }
        }
        return list;
    }

    private static long requiredLong(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing or invalid space SavedData long: " + key);
        }
        return tag.getLong(key);
    }
}
