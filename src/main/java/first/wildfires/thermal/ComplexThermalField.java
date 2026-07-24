package first.wildfires.thermal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

/**
 * Section cache for full thermal sources that use reflections and buoyancy.
 */
public final class ComplexThermalField {

    private static final int SECTION_SIZE = 16;
    private static final long REBUILD_DEBOUNCE_TICKS = 5L;
    private static final long MAXIMUM_DIRTY_TICKS = 40L;
    private static final int ACTIVE_SECTION_RADIUS = 4;
    private static final Map<FieldKey, FieldData> FIELDS = new HashMap<>();

    private ComplexThermalField() {
    }

    public static synchronized float get(Level level, BlockPos position) {
        FieldKey fieldKey = new FieldKey(level.dimension(), level.isClientSide());
        SectionKey targetSection = SectionKey.of(position);
        FieldData field = FIELDS.computeIfAbsent(fieldKey, ignored -> new FieldData());
        Map<SectionKey, CachedSection> sections = field.sections;
        CachedSection cached = sections.get(targetSection);
        if (cached == null || cached.dirty() && shouldRebuild(level.getGameTime(), cached)) {
            if (level.getGameTime() - field.lastRebuildTick >= REBUILD_DEBOUNCE_TICKS) {
                rebuildAroundSection(level, sections, targetSection);
                field.lastRebuildTick = level.getGameTime();
            }
            cached = sections.get(targetSection);
        }
        return cached == null ? 0.0F : cached.values().getOrDefault(position.asLong(), 0.0F);
    }

    public static synchronized void invalidateAround(Level level, BlockPos position) {
        FieldKey fieldKey = new FieldKey(level.dimension(), level.isClientSide());
        FieldData field = FIELDS.get(fieldKey);
        if (field == null) {
            return;
        }
        Map<SectionKey, CachedSection> sections = field.sections;

        int sectionRadius = Math.max(1, (ThermalSourceRegistry.getMaximumRadiationRadius() + SECTION_SIZE - 1) / SECTION_SIZE + 1);
        SectionKey center = SectionKey.of(position);
        for (int x = -sectionRadius; x <= sectionRadius; x++) {
            for (int y = -sectionRadius; y <= sectionRadius; y++) {
                for (int z = -sectionRadius; z <= sectionRadius; z++) {
                    SectionKey key = new SectionKey(center.x() + x, center.y() + y, center.z() + z);
                    CachedSection cached = sections.get(key);
                    if (cached != null) {
                        sections.put(key, cached.markDirty(level.getGameTime()));
                    }
                }
            }
        }
    }

    public static synchronized void clear(Level level) {
        FIELDS.remove(new FieldKey(level.dimension(), level.isClientSide()));
    }

    public static synchronized void prune(Level level, Collection<BlockPos> activeCenters) {
        FieldData field = FIELDS.get(new FieldKey(level.dimension(), level.isClientSide()));
        if (field == null || activeCenters.isEmpty()) {
            return;
        }
        field.sections.keySet().removeIf(key -> !isNearActiveCenter(key, activeCenters));
    }

    private static void rebuildAroundSection(Level level, Map<SectionKey, CachedSection> sections, SectionKey targetSection) {
        BlockPos center = new BlockPos(targetSection.x() * SECTION_SIZE + SECTION_SIZE / 2,
                targetSection.y() * SECTION_SIZE + SECTION_SIZE / 2,
                targetSection.z() * SECTION_SIZE + SECTION_SIZE / 2);
        ThermalGrid.rebuildAround(level, center);

        Map<SectionKey, Map<Long, Float>> rebuiltValues = new HashMap<>();
        for (Map.Entry<Long, Float> cell : ThermalGrid.snapshot(level).entrySet()) {
            SectionKey sectionKey = SectionKey.of(BlockPos.of(cell.getKey()));
            rebuiltValues.computeIfAbsent(sectionKey, ignored -> new HashMap<>()).put(cell.getKey(), cell.getValue());
        }
        for (Map.Entry<SectionKey, Map<Long, Float>> entry : rebuiltValues.entrySet()) {
            sections.put(entry.getKey(), new CachedSection(Map.copyOf(entry.getValue()), false, 0L, 0L));
        }
        sections.putIfAbsent(targetSection, new CachedSection(Map.of(), false, 0L, 0L));
    }

    private record FieldKey(ResourceKey<Level> dimension, boolean clientSide) {
    }

    private record SectionKey(int x, int y, int z) {
        private static SectionKey of(BlockPos position) {
            return new SectionKey(Math.floorDiv(position.getX(), SECTION_SIZE),
                    Math.floorDiv(position.getY(), SECTION_SIZE), Math.floorDiv(position.getZ(), SECTION_SIZE));
        }
    }

    private static boolean shouldRebuild(long currentTick, CachedSection section) {
        return currentTick - section.lastChangeTick() >= REBUILD_DEBOUNCE_TICKS
                || currentTick - section.dirtyAtTick() >= MAXIMUM_DIRTY_TICKS;
    }

    private record CachedSection(Map<Long, Float> values, boolean dirty, long dirtyAtTick, long lastChangeTick) {
        private CachedSection markDirty(long currentTick) {
            return dirty ? new CachedSection(values, true, dirtyAtTick, currentTick)
                    : new CachedSection(values, true, currentTick, currentTick);
        }
    }

    private static final class FieldData {
        private final Map<SectionKey, CachedSection> sections = new HashMap<>();
        private long lastRebuildTick = Long.MIN_VALUE / 2;
    }

    private static boolean isNearActiveCenter(SectionKey key, Collection<BlockPos> activeCenters) {
        for (BlockPos center : activeCenters) {
            SectionKey centerSection = SectionKey.of(center);
            if (Math.abs(key.x() - centerSection.x()) <= ACTIVE_SECTION_RADIUS
                    && Math.abs(key.y() - centerSection.y()) <= ACTIVE_SECTION_RADIUS
                    && Math.abs(key.z() - centerSection.z()) <= ACTIVE_SECTION_RADIUS) {
                return true;
            }
        }
        return false;
    }
}
