package first.wildfires.thermal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Light-style thermal field for simple sources. Temperatures are stored per Section
 * as fixed-point shorts and rebuilt through one shared maximum-value propagation queue.
 */
public final class SimpleThermalField {

    private static final int SECTION_SIZE = 16;
    private static final int SECTION_VOLUME = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
    private static final int TEMPERATURE_SCALE = 100;
    private static final int MINIMUM_TEMPERATURE = 50;
    private static final long REBUILD_DEBOUNCE_TICKS = 5L;
    private static final long MAXIMUM_DIRTY_TICKS = 40L;
    private static final int TARGET_DENSE_SOURCE_SEEDS = 4096;
    private static final int FULL_DETAIL_SEED_RADIUS = 12;
    private static final int ACTIVE_SECTION_RADIUS = 4;
    private static final Map<FieldKey, FieldData> FIELDS = new HashMap<>();

    private SimpleThermalField() {
    }

    public static synchronized float get(Level level, BlockPos position) {
        FieldData field = FIELDS.computeIfAbsent(FieldKey.of(level), ignored -> new FieldData());
        SectionKey sectionKey = SectionKey.of(position);
        ensureSourceIndex(level, field, sectionKey);
        ThermalSection section = field.sections.get(sectionKey);
        if (section == null || section.dirty() && shouldRebuild(level.getGameTime(), section)) {
            section = rebuildSection(level, field, sectionKey, position);
            field.sections.put(sectionKey, section);
        }
        if (section == null) {
            return 0.0F;
        }
        return section.values()[localIndex(position)] / (float) TEMPERATURE_SCALE;
    }

    public static synchronized void invalidateAround(Level level, BlockPos position) {
        FieldData field = FIELDS.get(FieldKey.of(level));
        if (field == null) {
            return;
        }

        refreshIndexedSources(level, field, position);
        int sectionRadius = Math.max(1, (ThermalSourceRegistry.getMaximumSimpleRadiationRadius() + SECTION_SIZE - 1) / SECTION_SIZE + 1);
        SectionKey center = SectionKey.of(position);
        for (int x = -sectionRadius; x <= sectionRadius; x++) {
            for (int y = -sectionRadius; y <= sectionRadius; y++) {
                for (int z = -sectionRadius; z <= sectionRadius; z++) {
                    SectionKey key = new SectionKey(center.x() + x, center.y() + y, center.z() + z);
                    ThermalSection cached = field.sections.get(key);
                    if (cached != null) {
                        field.sections.put(key, cached.markDirty(level.getGameTime()));
                    }
                }
            }
        }
    }

    public static synchronized void clear(Level level) {
        FIELDS.remove(FieldKey.of(level));
    }

    /** Keeps only the source index and results around currently active players. */
    public static synchronized void prune(Level level, Collection<BlockPos> activeCenters) {
        FieldData field = FIELDS.get(FieldKey.of(level));
        if (field == null || activeCenters.isEmpty()) {
            return;
        }
        field.sections.keySet().removeIf(key -> !isNearActiveCenter(key, activeCenters));
        field.sources.keySet().removeIf(key -> !isNearActiveCenter(key, activeCenters));
        field.indexedSourceSections.removeIf(key -> !isNearActiveCenter(key, activeCenters));
    }

    public static synchronized Map<Long, Float> snapshot(Level level) {
        FieldData field = FIELDS.get(FieldKey.of(level));
        if (field == null) {
            return Map.of();
        }
        Map<Long, Float> result = new HashMap<>();
        for (Map.Entry<SectionKey, ThermalSection> entry : field.sections.entrySet()) {
            ThermalSection section = entry.getValue();
            if (section.dirty()) {
                continue;
            }
            BlockPos origin = entry.getKey().origin();
            short[] values = section.values();
            for (int index = 0; index < values.length; index++) {
                if (Math.abs(values[index]) >= MINIMUM_TEMPERATURE) {
                    result.put(positionAt(origin, index).asLong(), values[index] / (float) TEMPERATURE_SCALE);
                }
            }
        }
        return Map.copyOf(result);
    }

    private static void ensureSourceIndex(Level level, FieldData field, SectionKey target) {
        int sectionRadius = (ThermalSourceRegistry.getMaximumSimpleRadiationRadius() + SECTION_SIZE - 1) / SECTION_SIZE;
        for (int x = -sectionRadius; x <= sectionRadius; x++) {
            for (int y = -sectionRadius; y <= sectionRadius; y++) {
                for (int z = -sectionRadius; z <= sectionRadius; z++) {
                    SectionKey key = new SectionKey(target.x() + x, target.y() + y, target.z() + z);
                    if (field.indexedSourceSections.add(key)) {
                        indexSourceSection(level, field, key);
                    }
                }
            }
        }
    }

    private static void indexSourceSection(Level level, FieldData field, SectionKey sectionKey) {
        Map<Long, SourceEntry> sources = field.sources.computeIfAbsent(sectionKey, ignored -> new HashMap<>());
        BlockPos origin = sectionKey.origin();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    refreshSourceAt(level, sources, cursor);
                }
            }
        }
    }

    private static void refreshIndexedSources(Level level, FieldData field, BlockPos position) {
        refreshIndexedSource(level, field, position);
        for (Direction direction : Direction.values()) {
            refreshIndexedSource(level, field, position.relative(direction));
        }
    }

    private static void refreshIndexedSource(Level level, FieldData field, BlockPos position) {
        SectionKey key = SectionKey.of(position);
        if (!field.indexedSourceSections.contains(key)) {
            return;
        }
        refreshSourceAt(level, field.sources.computeIfAbsent(key, ignored -> new HashMap<>()), position);
    }

    private static void refreshSourceAt(Level level, Map<Long, SourceEntry> sources, BlockPos position) {
        BlockState state = level.getBlockState(position);
        ThermalSourceRegistry.ThermalSourceDefinition definition = ThermalSourceRegistry.getDefinition(state);
        long key = position.asLong();
        if (definition == null || !definition.simpleHeatSource()) {
            sources.remove(key);
            return;
        }

        int temperature = toUnits(ThermalSourceRegistry.getRadiationTemperature(level, position, state));
        int attenuation = Math.max(1, toUnits(definition.getAttenuation(temperature / (float) TEMPERATURE_SCALE)));
        int openFaces = 0;
        for (Direction direction : Direction.values()) {
            BlockPos next = position.relative(direction);
            if (level.hasChunkAt(next) && isPassable(level, next, level.getBlockState(next))) {
                openFaces |= 1 << direction.ordinal();
            }
        }
        sources.put(key, new SourceEntry(temperature, attenuation, definition.radiationRadius(), openFaces));
    }

    private static ThermalSection rebuildSection(Level level, FieldData field, SectionKey target, BlockPos focus) {
        int radius = ThermalSourceRegistry.getMaximumSimpleRadiationRadius();
        BlockPos targetOrigin = target.origin();
        int minimumX = targetOrigin.getX() - radius;
        int minimumY = targetOrigin.getY() - radius;
        int minimumZ = targetOrigin.getZ() - radius;
        int size = SECTION_SIZE + radius * 2;
        int maximumX = minimumX + size - 1;
        int maximumY = minimumY + size - 1;
        int maximumZ = minimumZ + size - 1;
        short[] values = new short[size * size * size];
        byte[] passability = new byte[values.length];
        PrimitiveQueue queue = new PrimitiveQueue();
        int sourceStride = getSourceStride(field, minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ);

        for (Map<Long, SourceEntry> sourceSection : field.sources.values()) {
            for (Map.Entry<Long, SourceEntry> entry : sourceSection.entrySet()) {
                BlockPos sourcePosition = BlockPos.of(entry.getKey());
                if (sourcePosition.getX() < minimumX || sourcePosition.getX() > maximumX
                        || sourcePosition.getY() < minimumY || sourcePosition.getY() > maximumY
                        || sourcePosition.getZ() < minimumZ || sourcePosition.getZ() > maximumZ) {
                    continue;
                }
                if (!shouldSeedSource(sourcePosition, focus, sourceStride)) {
                    continue;
                }
                SourceEntry source = entry.getValue();
                for (Direction direction : Direction.values()) {
                    if ((source.openFaces() & (1 << direction.ordinal())) == 0) {
                        continue;
                    }
                    BlockPos next = sourcePosition.relative(direction);
                    if (!contains(next, minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)) {
                        continue;
                    }
                    offer(values, queue, gridIndex(next, minimumX, minimumY, minimumZ, size),
                            attenuate(source.temperature(), source.attenuation()), 1, source.attenuation(), source.radius());
                }
            }
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            int currentIndex = queue.index();
            int currentTemperature = queue.temperature();
            int currentSteps = queue.steps();
            int currentAttenuation = queue.attenuation();
            int currentRadius = queue.radius();
            queue.remove();
            if (values[currentIndex] != currentTemperature || currentSteps >= currentRadius) {
                continue;
            }
            int x = currentIndex % size;
            int yz = currentIndex / size;
            int y = yz % size;
            int z = yz / size;
            for (Direction direction : Direction.values()) {
                int nextX = x + direction.getStepX();
                int nextY = y + direction.getStepY();
                int nextZ = z + direction.getStepZ();
                if (nextX < 0 || nextY < 0 || nextZ < 0 || nextX >= size || nextY >= size || nextZ >= size) {
                    continue;
                }
                int nextIndex = nextX + size * (nextY + size * nextZ);
                if (!isPassable(level, cursor, passability, nextIndex, minimumX + nextX, minimumY + nextY, minimumZ + nextZ)) {
                    continue;
                }
                offer(values, queue, nextIndex, attenuate(currentTemperature, currentAttenuation), currentSteps + 1,
                        currentAttenuation, currentRadius);
            }
        }

        short[] sectionValues = new short[SECTION_VOLUME];
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    int local = x + SECTION_SIZE * (y + SECTION_SIZE * z);
                    int expanded = (x + radius) + size * ((y + radius) + size * (z + radius));
                    sectionValues[local] = values[expanded];
                }
            }
        }
        return new ThermalSection(sectionValues, false, 0L, 0L);
    }

    private static boolean isPassable(Level level, BlockPos.MutableBlockPos cursor, byte[] passability, int index,
                                      int x, int y, int z) {
        if (passability[index] == 0) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            passability[index] = (byte) (isPassable(level, cursor, state) ? 1 : 2);
        }
        return passability[index] == 1;
    }

    private static void offer(short[] values, PrimitiveQueue queue, int index, int temperature, int steps,
                              int attenuation, int radius) {
        if (Math.abs(temperature) < MINIMUM_TEMPERATURE || Math.abs(temperature) <= Math.abs(values[index])) {
            return;
        }
        values[index] = (short) temperature;
        queue.add(index, temperature, steps, attenuation, radius);
    }

    private static int attenuate(int temperature, int attenuation) {
        int magnitude = Math.max(0, Math.abs(temperature) - attenuation);
        return temperature < 0 ? -magnitude : magnitude;
    }

    private static int getSourceStride(FieldData field, int minimumX, int minimumY, int minimumZ,
                                       int maximumX, int maximumY, int maximumZ) {
        int sourceCount = 0;
        for (Map<Long, SourceEntry> section : field.sources.values()) {
            for (long position : section.keySet()) {
                BlockPos sourcePosition = BlockPos.of(position);
                if (contains(sourcePosition, minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ)) {
                    sourceCount++;
                }
            }
        }
        if (sourceCount <= TARGET_DENSE_SOURCE_SEEDS) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(Math.cbrt(sourceCount / (double) TARGET_DENSE_SOURCE_SEEDS)));
    }

    private static boolean shouldSeedSource(BlockPos position, BlockPos focus, int stride) {
        if (Math.abs(position.getX() - focus.getX()) <= FULL_DETAIL_SEED_RADIUS
                && Math.abs(position.getY() - focus.getY()) <= FULL_DETAIL_SEED_RADIUS
                && Math.abs(position.getZ() - focus.getZ()) <= FULL_DETAIL_SEED_RADIUS) {
            return true;
        }
        return stride == 1 || Math.floorMod(position.getX(), stride) == 0
                && Math.floorMod(position.getY(), stride) == 0
                && Math.floorMod(position.getZ(), stride) == 0;
    }

    private static int toUnits(float temperature) {
        return Math.round(temperature * TEMPERATURE_SCALE);
    }

    private static int localIndex(BlockPos position) {
        return Math.floorMod(position.getX(), SECTION_SIZE)
                + SECTION_SIZE * (Math.floorMod(position.getY(), SECTION_SIZE)
                + SECTION_SIZE * Math.floorMod(position.getZ(), SECTION_SIZE));
    }

    private static int gridIndex(BlockPos position, int minimumX, int minimumY, int minimumZ, int size) {
        return position.getX() - minimumX + size * (position.getY() - minimumY + size * (position.getZ() - minimumZ));
    }

    private static boolean contains(BlockPos position, int minimumX, int minimumY, int minimumZ,
                                    int maximumX, int maximumY, int maximumZ) {
        return position.getX() >= minimumX && position.getX() <= maximumX
                && position.getY() >= minimumY && position.getY() <= maximumY
                && position.getZ() >= minimumZ && position.getZ() <= maximumZ;
    }

    private static BlockPos positionAt(BlockPos origin, int index) {
        int x = index % SECTION_SIZE;
        int yz = index / SECTION_SIZE;
        return origin.offset(x, yz % SECTION_SIZE, yz / SECTION_SIZE);
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

    private static boolean isPassable(Level level, BlockPos position, BlockState state) {
        return state.isAir() || state.getCollisionShape(level, position).isEmpty();
    }

    private static boolean shouldRebuild(long currentTick, ThermalSection section) {
        return currentTick - section.lastChangeTick() >= REBUILD_DEBOUNCE_TICKS
                || currentTick - section.dirtyAtTick() >= MAXIMUM_DIRTY_TICKS;
    }

    private record FieldKey(ResourceKey<Level> dimension, boolean clientSide) {
        private static FieldKey of(Level level) {
            return new FieldKey(level.dimension(), level.isClientSide());
        }
    }

    private record SectionKey(int x, int y, int z) {
        private static SectionKey of(BlockPos position) {
            return new SectionKey(Math.floorDiv(position.getX(), SECTION_SIZE), Math.floorDiv(position.getY(), SECTION_SIZE),
                    Math.floorDiv(position.getZ(), SECTION_SIZE));
        }

        private BlockPos origin() {
            return new BlockPos(x * SECTION_SIZE, y * SECTION_SIZE, z * SECTION_SIZE);
        }
    }

    private record SourceEntry(int temperature, int attenuation, int radius, int openFaces) {
    }

    private record ThermalSection(short[] values, boolean dirty, long dirtyAtTick, long lastChangeTick) {
        private ThermalSection markDirty(long tick) {
            // Batch ongoing edits, but retain the first dirty tick as a hard upper bound.
            return dirty ? new ThermalSection(values, true, dirtyAtTick, tick)
                    : new ThermalSection(values, true, tick, tick);
        }
    }

    private static final class FieldData {
        private final Map<SectionKey, ThermalSection> sections = new HashMap<>();
        private final Map<SectionKey, Map<Long, SourceEntry>> sources = new HashMap<>();
        private final Set<SectionKey> indexedSourceSections = new HashSet<>();
    }

    private static final class PrimitiveQueue {
        private int[] indexes = new int[1024];
        private int[] temperatures = new int[1024];
        private int[] steps = new int[1024];
        private int[] attenuations = new int[1024];
        private int[] radii = new int[1024];
        private int head;
        private int tail;

        private void add(int index, int temperature, int stepCount, int attenuation, int radius) {
            if (tail == indexes.length) {
                if (head > 0) {
                    int length = tail - head;
                    System.arraycopy(indexes, head, indexes, 0, length);
                    System.arraycopy(temperatures, head, temperatures, 0, length);
                    System.arraycopy(steps, head, steps, 0, length);
                    System.arraycopy(attenuations, head, attenuations, 0, length);
                    System.arraycopy(radii, head, radii, 0, length);
                    tail -= head;
                    head = 0;
                } else {
                    int expandedLength = indexes.length * 2;
                    indexes = java.util.Arrays.copyOf(indexes, expandedLength);
                    temperatures = java.util.Arrays.copyOf(temperatures, expandedLength);
                    steps = java.util.Arrays.copyOf(steps, expandedLength);
                    attenuations = java.util.Arrays.copyOf(attenuations, expandedLength);
                    radii = java.util.Arrays.copyOf(radii, expandedLength);
                }
            }
            indexes[tail] = index;
            temperatures[tail] = temperature;
            steps[tail] = stepCount;
            attenuations[tail] = attenuation;
            radii[tail] = radius;
            tail++;
        }

        private int index() {
            return indexes[head];
        }

        private int temperature() {
            return temperatures[head];
        }

        private int steps() {
            return steps[head];
        }

        private int attenuation() {
            return attenuations[head];
        }

        private int radius() {
            return radii[head];
        }

        private void remove() {
            head++;
        }

        private boolean isEmpty() {
            return head == tail;
        }
    }
}
