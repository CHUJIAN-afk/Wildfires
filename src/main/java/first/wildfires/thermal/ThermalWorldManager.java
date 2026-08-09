package first.wildfires.thermal;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/** Server-authoritative sparse Section air field, source-face index, scheduler, and persistence facade. */
public final class ThermalWorldManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final float SAVE_THRESHOLD = 0.1F;
    public static final float WAKE_THRESHOLD = 0.01F;
    public static final float STABLE_THRESHOLD = 0.005F;
    public static final int DATA_VERSION = 4;
    public static final String CHUNK_DATA_KEY = "wildfires_thermal";

    private static final float STANDARD_STEP_SECONDS = 0.125F;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int NEAR_INTERVAL = 10;
    private static final int RECENT_INTERVAL = 20;
    private static final int IDLE_INTERVAL = 100;
    private static final int RECENT_GRACE_TICKS = 20 * 60;
    private static final int ACTIVE_CHUNK_RADIUS = 2;
    private static final int DYNAMIC_SOURCE_INTERVAL = 10;
    private static final int PLAYER_SAMPLE_INTERVAL = 10;
    private static final int RADIATION_QUERY_RETENTION_TICKS = PLAYER_SAMPLE_INTERVAL * 2;
    private static final int RESIDUAL_SWEEP_INTERVAL = 100;
    private static final int MAX_PATCH_SPAN = 4;
    private static final float RADIATION_TRACKING_PADDING = MAX_PATCH_SPAN + 1.0F;
    private static final int SIMULATION_PARALLELISM = Math.max(1,
            Math.min(4, Runtime.getRuntime().availableProcessors() - 2));
    private static final AtomicInteger SIMULATION_WORKER_IDS = new AtomicInteger();
    private static final Map<ServerLevel, ThermalWorldManager> INSTANCES = new WeakHashMap<>();

    private final ServerLevel level;
    private final ForkJoinPool simulationPool;
    private final Map<Long, ThermalSection> sections = new HashMap<>();
    private final Long2ObjectOpenHashMap<SourceRecord> sources = new Long2ObjectOpenHashMap<>();
    private final Map<Long, Integer> sourceCountsBySection = new HashMap<>();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> sourcePositionsByChunk =
            new Long2ObjectOpenHashMap<>();
    private final Map<Long, List<ThermalFace>> heatingFacesByTarget = new HashMap<>();
    private final Map<Long, List<ThermalFace>> radiantFacesBySection = new HashMap<>();
    private final Map<Long, List<RadiantPatch>> radiantPatchesBySection = new HashMap<>();
    private final Map<Long, List<BoundaryContact>> boundaryContactCache = new HashMap<>();
    private final Map<Long, CompoundTag> pendingUnloadedChunkData = new HashMap<>();
    private final Set<Long> pendingChunkLoads = new HashSet<>();
    private final Map<UUID, CachedPlayerSample> playerSamples = new HashMap<>();
    private final LongSet radiationReceiverSections = new LongOpenHashSet();
    private final Long2IntOpenHashMap radiationReceiverMinimumRanges = new Long2IntOpenHashMap();
    private final LongSet radiationCoverageChunks = new LongOpenHashSet();
    private final Long2LongOpenHashMap transientRadiationReceiverSections = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap transientRadiationDebugReceiverSections = new Long2LongOpenHashMap();
    private final Long2IntOpenHashMap transientRadiationReceiverMinimumRanges = new Long2IntOpenHashMap();
    private float maximumRegisteredRadiationRange = -1.0F;
    private boolean radiantPatchesDirty = true;
    private float maximumLoadedRadiationRange;
    private long lastDynamicSourcePoll;
    private int lastProcessedCells;
    private int lastDeferredSections;
    private boolean lastLaplacianEnabled;
    private float lastLaplacianCoefficient;
    private float lastBuoyancyCoefficient;
    private float lastColdSinkingCoefficient;
    private boolean lastRadiationEnabled;
    private float lastDefaultSolidLoss;
    private float lastAirTemperatureCutoff;
    private float lastHiddenTemperatureCutoff;
    private long simulationRevision;
    @Nullable
    private ForkJoinTask<ThermalSimulationEngine.BatchResult> inFlightSimulation;
    @Nullable
    private ThermalSimulationEngine.CancellationToken inFlightCancellation;
    private boolean closed;

    private ThermalWorldManager(ServerLevel level) {
        this.level = level;
        this.simulationPool = new ForkJoinPool(SIMULATION_PARALLELISM, pool -> {
            var worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("Wildfires-Thermal-" + SIMULATION_WORKER_IDS.incrementAndGet());
            worker.setDaemon(true);
            return worker;
        }, (thread, throwable) -> LOGGER.error("Uncaught asynchronous thermal simulation failure", throwable), true);
        lastLaplacianEnabled = ThermalConfig.laplacianEnabled();
        lastLaplacianCoefficient = ThermalConfig.laplacianCoefficient();
        lastBuoyancyCoefficient = ThermalConfig.buoyancyCoefficient();
        lastColdSinkingCoefficient = ThermalConfig.coldSinkingCoefficient();
        lastRadiationEnabled = ThermalConfig.radiationEnabled();
        lastDefaultSolidLoss = ThermalConfig.defaultSolidLoss();
        lastAirTemperatureCutoff = ThermalConfig.airTemperatureCutoff();
        lastHiddenTemperatureCutoff = ThermalConfig.hiddenTemperatureCutoff();
    }

    public static synchronized ThermalWorldManager get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, ThermalWorldManager::new);
    }

    public static synchronized void clear(ServerLevel level) {
        ThermalWorldManager manager = INSTANCES.remove(level);
        if (manager != null) {
            manager.close();
        }
        ThermalRadiationSolver.clear(level);
    }

    public static synchronized Collection<ThermalWorldManager> instances() {
        return List.copyOf(INSTANCES.values());
    }

    public void tick() {
        if (closed) {
            return;
        }
        long gameTick = level.getGameTime();
        refreshRadiationCoverage(gameTick);
        processPendingChunkLoads();
        refreshAfterConfigChanges();
        if (gameTick - lastDynamicSourcePoll >= DYNAMIC_SOURCE_INTERVAL) {
            pollDynamicSources();
            lastDynamicSourcePoll = gameTick;
        }
        if (!commitCompletedSimulation()) {
            return;
        }
        float airTemperatureCutoff = ThermalConfig.airTemperatureCutoff();
        float hiddenTemperatureCutoff = ThermalConfig.hiddenTemperatureCutoff();
        List<SimulationJob> dueJobs = new ArrayList<>();
        for (ThermalSection section : sections.values()) {
            boolean nearPlayer = isNearPlayer(section);
            if (nearPlayer && !section.nearPlayer) {
                wakeStoredTemperatures(section);
            }
            section.nearPlayer = nearPlayer;
            safetySweepInvalidTemperatures(section, gameTick, airTemperatureCutoff, hiddenTemperatureCutoff);
            if (!section.forceDue && section.active.isEmpty()) {
                continue;
            }
            Scheduling scheduling = schedulingFor(section, gameTick, nearPlayer);
            long elapsedTicks = Math.max(0L, gameTick - section.lastSimulationTick);
            if (!section.forceDue && elapsedTicks < scheduling.intervalTicks()) {
                continue;
            }
            if (elapsedTicks == 0L) {
                continue;
            }
            float elapsedSeconds = elapsedTicks / 20.0F;
            int desiredSubsteps = Math.max(1, (int) Math.ceil(elapsedSeconds / STANDARD_STEP_SECONDS));
            int substeps = Math.min(scheduling.maximumSubsteps(), desiredSubsteps);
            float stepSeconds = elapsedSeconds / substeps;
            int estimatedWork = Math.max(1, ThermalSimulationEngine.expandedActiveCells(section.active).cardinality())
                    * substeps;
            double urgency = section.forceDue
                    ? 1_000_000.0D + elapsedTicks
                    : elapsedTicks / (double) scheduling.intervalTicks();
            dueJobs.add(new SimulationJob(section, substeps, stepSeconds, estimatedWork, urgency));
        }
        dueJobs.sort(Comparator.comparingDouble(SimulationJob::urgency).reversed()
                .thenComparingLong(job -> job.section().lastSimulationTick));

        int remainingBudget = ThermalConfig.cellBudgetPerTick();
        List<SimulationJob> selected = new ArrayList<>();
        lastDeferredSections = 0;
        for (SimulationJob job : dueJobs) {
            if (job.estimatedWork() > remainingBudget && selected.size() >= SIMULATION_PARALLELISM) {
                lastDeferredSections++;
                continue;
            }
            selected.add(job);
            remainingBudget = Math.max(0, remainingBudget - job.estimatedWork());
        }
        if (!selected.isEmpty()) {
            ThermalSimulationEngine.CancellationToken cancellationToken =
                    new ThermalSimulationEngine.CancellationToken();
            ThermalSimulationEngine.BatchInput batch = createSimulationBatch(selected, gameTick, cancellationToken);
            inFlightCancellation = cancellationToken;
            inFlightSimulation = simulationPool.submit(() -> ThermalSimulationEngine.solve(batch));
        }
        Set<Long> submittedSections = new HashSet<>();
        for (SimulationJob job : selected) {
            submittedSections.add(job.section().sectionKey);
        }
        pruneEmptySections(submittedSections);
    }

    /** Commits only a complete result produced from the current world revision. */
    private boolean commitCompletedSimulation() {
        ForkJoinTask<ThermalSimulationEngine.BatchResult> task = inFlightSimulation;
        if (task == null) {
            return true;
        }
        if (!task.isDone()) {
            return false;
        }
        inFlightSimulation = null;
        inFlightCancellation = null;
        ThermalSimulationEngine.BatchResult batch;
        try {
            batch = task.get();
        } catch (CancellationException ignored) {
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException exception) {
            LOGGER.error("Asynchronous thermal simulation failed; preserving the last committed field", exception.getCause());
            return true;
        }
        if (closed || batch.revision() != simulationRevision) {
            return true;
        }
        for (long sectionKey : batch.sections().keySet()) {
            if (!sections.containsKey(sectionKey)) {
                invalidateSimulation();
                return true;
            }
        }
        for (ThermalSimulationEngine.SectionResult result : batch.sections().values()) {
            ThermalSection section = sections.get(result.sectionKey());
            section.replaceSimulationState(result.visible(), result.hidden(), result.hiddenCount(),
                    result.active(), result.stableCycles(), result.forceDue());
            section.lastSimulationTick = batch.simulationTick();
            if (result.storageChanged()) {
                markChunkUnsaved(section);
            }
        }
        for (ThermalSimulationEngine.CellRef activation : batch.externalActivations()) {
            BlockPos position = positionAt(activation.sectionKey(), activation.localIndex());
            if (isLoadedAirMedium(position)) {
                ensureSection(position).wake(activation.localIndex());
            }
        }
        lastProcessedCells = batch.processedCells();
        return true;
    }

    private ThermalSimulationEngine.BatchInput createSimulationBatch(List<SimulationJob> selected,
                                                                      long simulationTick,
                                                                      ThermalSimulationEngine.CancellationToken cancellationToken) {
        LongSet selectedKeys = new LongOpenHashSet(selected.size());
        Long2ObjectOpenHashMap<BitSet> prepared = new Long2ObjectOpenHashMap<>(selected.size());
        int maximumSubsteps = 1;
        for (SimulationJob job : selected) {
            long key = job.section().sectionKey;
            selectedKeys.add(key);
            prepared.put(key, (BitSet) job.section().active.clone());
            maximumSubsteps = Math.max(maximumSubsteps, job.substeps());
        }
        // One substep expands active cells once and a representation change can wake one further
        // shell. Radius 2*n includes every cell that can be updated plus its read-only halo.
        for (int round = 0; round < maximumSubsteps * 2; round++) {
            prepared = expandPreparedMasks(prepared);
        }

        Long2ObjectOpenHashMap<BitSet> airCells = new Long2ObjectOpenHashMap<>();
        LongSet snapshotKeys = new LongOpenHashSet(selectedKeys);
        for (Long2ObjectMap.Entry<BitSet> entry : prepared.long2ObjectEntrySet()) {
            long sectionKey = entry.getLongKey();
            BitSet preparedCells = entry.getValue();
            for (int index = preparedCells.nextSetBit(0); index >= 0;
                 index = preparedCells.nextSetBit(index + 1)) {
                snapshotAirCell(sectionKey, index, airCells, snapshotKeys);
                for (int direction = 0; direction < ThermalSimulationEngine.DIRECTION_COUNT; direction++) {
                    int neighborCode = ThermalSimulationEngine.neighborCode(index, direction);
                    long neighborSectionKey = (neighborCode & ThermalSimulationEngine.CROSS_SECTION_FLAG) == 0
                            ? sectionKey : offsetSectionKey(sectionKey, direction);
                    snapshotAirCell(neighborSectionKey,
                            neighborCode & ThermalSimulationEngine.LOCAL_INDEX_MASK, airCells, snapshotKeys);
                }
            }
        }

        Map<Long, ThermalSimulationEngine.SectionInput> sectionInputs = new HashMap<>(snapshotKeys.size());
        for (LongIterator keys = snapshotKeys.iterator(); keys.hasNext(); ) {
            long key = keys.nextLong();
            ThermalSection stored = sections.get(key);
            float[] visible = stored == null ? new float[ThermalSection.VOLUME] : stored.current.clone();
            float[] hidden = stored == null ? new float[ThermalSection.VOLUME] : stored.hiddenSnapshot();
            BitSet active = stored == null ? new BitSet(ThermalSection.VOLUME) : (BitSet) stored.active.clone();
            BitSet preparedCells = prepared.get(key);
            if (preparedCells == null) {
                preparedCells = new BitSet(ThermalSection.VOLUME);
            }
            BitSet snapshotAirCells = airCells.get(key);
            if (snapshotAirCells == null) {
                snapshotAirCells = new BitSet(ThermalSection.VOLUME);
            }
            ThermalSimulationEngine.BoundaryTerm[][] boundaries = new ThermalSimulationEngine.BoundaryTerm[ThermalSection.VOLUME][];
            ThermalSimulationEngine.HeatSource[][] heatSources = new ThermalSimulationEngine.HeatSource[ThermalSection.VOLUME][];
            if (selectedKeys.contains(key)) {
                for (int index = preparedCells.nextSetBit(0); index >= 0;
                     index = preparedCells.nextSetBit(index + 1)) {
                    if (!snapshotAirCells.get(index)) {
                        continue;
                    }
                    BlockPos position = positionAt(key, index);
                    boundaries[index] = snapshotBoundaryTerms(position);
                    heatSources[index] = snapshotHeatSources(position);
                }
            }
            sectionInputs.put(key, ThermalSimulationEngine.SectionInput.owned(key, visible, hidden,
                    snapshotAirCells, preparedCells, active, stored == null ? 0 : stored.stableCycles,
                    stored != null && stored.forceDue, neighborSectionKeys(key), boundaries, heatSources));
        }

        List<ThermalSimulationEngine.JobInput> jobs = new ArrayList<>(selected.size());
        for (SimulationJob job : selected) {
            jobs.add(new ThermalSimulationEngine.JobInput(job.section().sectionKey,
                    job.substeps(), job.stepSeconds()));
        }
        ThermalSimulationEngine.Settings settings = new ThermalSimulationEngine.Settings(
                ThermalConfig.laplacianEnabled(), ThermalConfig.laplacianCoefficient(),
                ThermalConfig.buoyancyCoefficient(), ThermalConfig.coldSinkingCoefficient(),
                ThermalConfig.airTemperatureCutoff(), ThermalConfig.hiddenTemperatureCutoff());
        return new ThermalSimulationEngine.BatchInput(simulationRevision, simulationTick, settings,
                jobs, sectionInputs, cancellationToken);
    }

    private void snapshotAirCell(long sectionKey, int localIndex,
                                 Long2ObjectOpenHashMap<BitSet> airCells, LongSet snapshotKeys) {
        BlockPos position = positionAt(sectionKey, localIndex);
        if (cachedAirMedium(position)) {
            snapshotKeys.add(sectionKey);
            airCells.computeIfAbsent(sectionKey, ignored -> new BitSet(ThermalSection.VOLUME))
                    .set(localIndex);
        }
    }

    private boolean cachedAirMedium(BlockPos position) {
        if (level.isOutsideBuildHeight(position) || !level.hasChunkAt(position)) {
            return false;
        }
        ThermalSection section = ensureSection(position);
        int index = localIndex(position);
        if (!section.airCacheKnown.get(index)) {
            if (isAirMedium(position)) {
                section.airCells.set(index);
            } else {
                section.airCells.clear(index);
            }
            section.airCacheKnown.set(index);
        }
        return section.airCells.get(index);
    }

    private ThermalSimulationEngine.BoundaryTerm[] snapshotBoundaryTerms(BlockPos position) {
        long positionKey = position.asLong();
        ThermalSection section = sections.get(sectionKey(position));
        int index = localIndex(position);
        boolean cacheKnown = section != null && section.boundaryCacheKnown.get(index);
        List<BoundaryContact> contacts = boundaryContactCache.get(positionKey);
        if (!cacheKnown) {
            contacts = buildBoundaryContacts(position);
            if (contacts.isEmpty()) {
                boundaryContactCache.remove(positionKey);
            } else {
                boundaryContactCache.put(positionKey, contacts);
            }
            if (section != null) {
                section.boundaryCacheKnown.set(index);
            }
        } else if (contacts == null) {
            contacts = List.of();
        }
        List<ThermalSimulationEngine.BoundaryTerm> terms = new ArrayList<>();
        for (BoundaryContact contact : contacts) {
            if (!isActiveSourceFace(contact.solidPosition())) {
                terms.add(new ThermalSimulationEngine.BoundaryTerm(
                        ThermalBoundaryRegistry.getLoss(contact.solidState()), contact.area()));
            }
        }
        return terms.isEmpty() ? null : terms.toArray(ThermalSimulationEngine.BoundaryTerm[]::new);
    }

    private ThermalSimulationEngine.HeatSource[] snapshotHeatSources(BlockPos position) {
        List<ThermalFace> faces = heatingFacesByTarget.get(position.asLong());
        if (faces == null || faces.isEmpty()) {
            return null;
        }
        ThermalSimulationEngine.HeatSource[] result = new ThermalSimulationEngine.HeatSource[faces.size()];
        for (int index = 0; index < faces.size(); index++) {
            ThermalFace face = faces.get(index);
            result[index] = new ThermalSimulationEngine.HeatSource(face.surfaceTemperature(),
                    face.faceHeatingRate(), face.area());
        }
        return result;
    }

    private static Long2ObjectOpenHashMap<BitSet> expandPreparedMasks(Long2ObjectMap<BitSet> previous) {
        Long2ObjectOpenHashMap<BitSet> expanded = new Long2ObjectOpenHashMap<>(previous.size());
        for (Long2ObjectMap.Entry<BitSet> entry : previous.long2ObjectEntrySet()) {
            expanded.put(entry.getLongKey(), (BitSet) entry.getValue().clone());
        }
        for (Long2ObjectMap.Entry<BitSet> entry : previous.long2ObjectEntrySet()) {
            long sectionKey = entry.getLongKey();
            BitSet localExpanded = expanded.get(sectionKey);
            BitSet[] neighborExpanded = new BitSet[ThermalSimulationEngine.DIRECTION_COUNT];
            for (int direction = 0; direction < ThermalSimulationEngine.DIRECTION_COUNT; direction++) {
                neighborExpanded[direction] = expanded.get(offsetSectionKey(sectionKey, direction));
            }
            ThermalSimulationEngine.expandPreparedMask(entry.getValue(), localExpanded, neighborExpanded);
        }
        return expanded;
    }

    private static long offsetSectionKey(long sectionKey, int direction) {
        return switch (direction) {
            case ThermalSimulationEngine.WEST -> offsetSectionKey(sectionKey, -1, 0, 0);
            case ThermalSimulationEngine.EAST -> offsetSectionKey(sectionKey, 1, 0, 0);
            case ThermalSimulationEngine.DOWN -> offsetSectionKey(sectionKey, 0, -1, 0);
            case ThermalSimulationEngine.UP -> offsetSectionKey(sectionKey, 0, 1, 0);
            case ThermalSimulationEngine.NORTH -> offsetSectionKey(sectionKey, 0, 0, -1);
            case ThermalSimulationEngine.SOUTH -> offsetSectionKey(sectionKey, 0, 0, 1);
            default -> throw new IllegalArgumentException("Unknown direction " + direction);
        };
    }

    private static long offsetSectionKey(long key, int offsetX, int offsetY, int offsetZ) {
        return SectionPos.asLong(SectionPos.x(key) + offsetX, SectionPos.y(key) + offsetY,
                SectionPos.z(key) + offsetZ);
    }

    private static long[] neighborSectionKeys(long key) {
        int x = SectionPos.x(key);
        int y = SectionPos.y(key);
        int z = SectionPos.z(key);
        return new long[]{SectionPos.asLong(x - 1, y, z), SectionPos.asLong(x + 1, y, z),
                SectionPos.asLong(x, y - 1, z), SectionPos.asLong(x, y + 1, z),
                SectionPos.asLong(x, y, z - 1), SectionPos.asLong(x, y, z + 1)};
    }

    private void invalidateSimulation() {
        simulationRevision++;
        if (inFlightCancellation != null) {
            inFlightCancellation.cancel();
        }
        if (inFlightSimulation != null) {
            inFlightSimulation.cancel(true);
        }
    }

    private void close() {
        closed = true;
        invalidateSimulation();
        inFlightSimulation = null;
        inFlightCancellation = null;
        simulationPool.shutdownNow();
        playerSamples.clear();
        pendingChunkLoads.clear();
    }

    private void refreshAfterConfigChanges() {
        boolean laplacian = ThermalConfig.laplacianEnabled();
        float laplacianCoefficient = ThermalConfig.laplacianCoefficient();
        float buoyancy = ThermalConfig.buoyancyCoefficient();
        float coldSinking = ThermalConfig.coldSinkingCoefficient();
        boolean radiation = ThermalConfig.radiationEnabled();
        float solidLoss = ThermalConfig.defaultSolidLoss();
        float airTemperatureCutoff = ThermalConfig.airTemperatureCutoff();
        float hiddenTemperatureCutoff = ThermalConfig.hiddenTemperatureCutoff();
        boolean cutoffChanged = Float.floatToIntBits(airTemperatureCutoff)
                != Float.floatToIntBits(lastAirTemperatureCutoff)
                || Float.floatToIntBits(hiddenTemperatureCutoff)
                != Float.floatToIntBits(lastHiddenTemperatureCutoff);
        boolean solverChanged = laplacian != lastLaplacianEnabled
                || Float.floatToIntBits(laplacianCoefficient)
                != Float.floatToIntBits(lastLaplacianCoefficient)
                || Float.floatToIntBits(buoyancy) != Float.floatToIntBits(lastBuoyancyCoefficient)
                || Float.floatToIntBits(coldSinking) != Float.floatToIntBits(lastColdSinkingCoefficient)
                || Float.floatToIntBits(solidLoss) != Float.floatToIntBits(lastDefaultSolidLoss)
                || cutoffChanged;
        if (solverChanged) {
            invalidateSimulation();
            for (ThermalSection section : sections.values()) {
                for (int index = 0; index < section.current.length; index++) {
                    if (Math.abs(section.current[index]) >= 0.0001F
                            || Math.abs(section.getHiddenTemperature(index)) >= 0.0001F) {
                        section.wake(index);
                    }
                }
            }
            for (List<ThermalFace> faces : heatingFacesByTarget.values()) {
                for (ThermalFace face : faces) {
                    markActive(face.target());
                }
            }
        }
        if (radiation != lastRadiationEnabled) {
            playerSamples.clear();
            ThermalRadiationSolver.invalidate(level);
        }
        lastLaplacianEnabled = laplacian;
        lastLaplacianCoefficient = laplacianCoefficient;
        lastBuoyancyCoefficient = buoyancy;
        lastColdSinkingCoefficient = coldSinking;
        lastRadiationEnabled = radiation;
        lastDefaultSolidLoss = solidLoss;
        lastAirTemperatureCutoff = airTemperatureCutoff;
        lastHiddenTemperatureCutoff = hiddenTemperatureCutoff;
    }

    public float getAirTemperature(BlockPos position) {
        ThermalSection section = sections.get(sectionKey(position));
        return section == null ? 0.0F : section.get(localIndex(position));
    }

    public ThermalSample sample(ServerPlayer player) {
        refreshRadiationCoverage(level.getGameTime());
        CachedPlayerSample cached = playerSamples.get(player.getUUID());
        long gameTick = level.getGameTime();
        if (cached != null && gameTick - cached.tick() < PLAYER_SAMPLE_INTERVAL) {
            return cached.sample();
        }
        BlockPos position = player.blockPosition();
        float air = getAirTemperature(position);
        float effective = ThermalRadiationSolver.sample(level, this, player, position, air);
        ThermalSample sample = new ThermalSample(air, effective - air, effective);
        playerSamples.put(player.getUUID(), new CachedPlayerSample(gameTick, sample));
        return sample;
    }

    public ThermalSample sample(BlockPos position) {
        registerTransientRadiationReceiver(position, level.getGameTime());
        float air = getAirTemperature(position);
        float effective = ThermalRadiationSolver.sample(level, this, null, position, air);
        return new ThermalSample(air, effective - air, effective);
    }

    public void onBlockChanged(BlockPos position) {
        invalidateSimulation();
        playerSamples.clear();
        invalidateBoundaryContacts(position);
        ThermalSection cachedSection = sections.get(sectionKey(position));
        if (cachedSection != null) {
            cachedSection.invalidateAirCache(localIndex(position));
        }
        boolean airRelevant = sections.containsKey(sectionKey(position));
        boolean radiationRelevant = false;
        refreshSourceAt(position);
        SourceRecord changedSource = sources.get(position.asLong());
        airRelevant |= changedSource != null && changedSource.definition().heatsAir();
        radiationRelevant |= changedSource != null && changedSource.definition().radiates();
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbor = position.relative(direction);
            refreshSourceAt(neighbor);
            SourceRecord neighboringSource = sources.get(neighbor.asLong());
            airRelevant |= sections.containsKey(sectionKey(neighbor))
                    || neighboringSource != null && neighboringSource.definition().heatsAir();
            radiationRelevant |= neighboringSource != null && neighboringSource.definition().radiates();
        }
        ThermalSection changedSection = sections.get(sectionKey(position));
        if (changedSection != null && !isAirMedium(position)) {
            int index = localIndex(position);
            boolean persistedTemperatureRemoved = changedSection.current[index] != 0.0F;
            persistedTemperatureRemoved |= changedSection.getHiddenTemperature(index) != 0.0F;
            changedSection.current[index] = 0.0F;
            changedSection.clearHiddenTemperature(index);
            changedSection.active.clear(index);
            if (persistedTemperatureRemoved) {
                markChunkUnsaved(changedSection);
            }
        }
        if (airRelevant || radiationRelevant) {
            ThermalRadiationSolver.invalidate(level);
        }
        if (airRelevant) {
            wakeAround(position);
        }
    }

    public void loadChunk(LevelChunk chunk) {
        invalidateSimulation();
        playerSamples.clear();
        clearBoundaryContactCache();
        for (ThermalSection section : sections.values()) {
            if (SectionPos.x(section.sectionKey) == chunk.getPos().x
                    && SectionPos.z(section.sectionKey) == chunk.getPos().z) {
                section.clearAirCache();
            }
        }
        scanChunkSources(chunk, true, radiationCoverageChunks.contains(chunk.getPos().toLong()));
        refreshChunkBorders(chunk.getPos());
        ThermalRadiationSolver.invalidate(level);
    }

    /**
     * ChunkEvent.Load fires while the full-chunk future is still being completed. Reading that
     * chunk back through ServerLevel from inside the event can make the server thread wait on its
     * own future, so source discovery is deferred until the level's end-of-tick callback.
     */
    public void queueChunkLoad(ChunkPos chunkPos) {
        pendingChunkLoads.add(chunkPos.toLong());
    }

    private void processPendingChunkLoads() {
        if (pendingChunkLoads.isEmpty()) {
            return;
        }
        for (long chunkKey : List.copyOf(pendingChunkLoads)) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk != null) {
                loadChunk(chunk);
                pendingChunkLoads.remove(chunkKey);
            }
        }
    }

    /**
     * Keeps radiation-only runtime records local to actual receivers. Air-heating sources remain
     * globally indexed because their persistent field must continue evolving without a player nearby.
     */
    private void refreshRadiationCoverage(long gameTick) {
        LongIterator transientIterator = transientRadiationReceiverSections.keySet().iterator();
        while (transientIterator.hasNext()) {
            long sectionKey = transientIterator.nextLong();
            if (transientRadiationReceiverSections.get(sectionKey) < gameTick) {
                transientIterator.remove();
            }
        }
        LongIterator debugIterator = transientRadiationDebugReceiverSections.keySet().iterator();
        while (debugIterator.hasNext()) {
            long sectionKey = debugIterator.nextLong();
            if (transientRadiationDebugReceiverSections.get(sectionKey) < gameTick) {
                debugIterator.remove();
                transientRadiationReceiverMinimumRanges.remove(sectionKey);
            }
        }

        LongSet desiredReceivers = new LongOpenHashSet();
        Long2IntOpenHashMap desiredMinimumRanges = new Long2IntOpenHashMap();
        if (ThermalConfig.radiationEnabled()) {
            for (ServerPlayer player : level.players()) {
                desiredReceivers.add(sectionKey(player.blockPosition()));
            }
            desiredReceivers.addAll(transientRadiationReceiverSections.keySet());
            desiredReceivers.addAll(transientRadiationDebugReceiverSections.keySet());
            LongIterator transientRangeIterator = transientRadiationReceiverMinimumRanges.keySet().iterator();
            while (transientRangeIterator.hasNext()) {
                long receiver = transientRangeIterator.nextLong();
                int minimumRange = transientRadiationReceiverMinimumRanges.get(receiver);
                if (minimumRange > 0) {
                    desiredMinimumRanges.put(receiver, minimumRange);
                }
            }
        }

        float registeredMaximumRange = ThermalSourceRegistry.getMaximumNaturalRadiationRange();
        boolean registeredRangeChanged = Float.floatToIntBits(registeredMaximumRange)
                != Float.floatToIntBits(maximumRegisteredRadiationRange);
        if (!registeredRangeChanged && desiredReceivers.equals(radiationReceiverSections)
                && desiredMinimumRanges.equals(radiationReceiverMinimumRanges)) {
            return;
        }

        LongSet receiversToScan = new LongOpenHashSet();
        LongIterator desiredIterator = desiredReceivers.iterator();
        while (desiredIterator.hasNext()) {
            long receiver = desiredIterator.nextLong();
            int desiredRange = desiredMinimumRanges.getOrDefault(receiver, 0);
            if (registeredMaximumRange > maximumRegisteredRadiationRange
                    || !radiationReceiverSections.contains(receiver)
                    || desiredRange > radiationReceiverMinimumRanges.getOrDefault(receiver, 0)) {
                receiversToScan.add(receiver);
            }
        }

        radiationReceiverSections.clear();
        radiationReceiverSections.addAll(desiredReceivers);
        radiationReceiverMinimumRanges.clear();
        radiationReceiverMinimumRanges.putAll(desiredMinimumRanges);
        maximumRegisteredRadiationRange = registeredMaximumRange;

        LongSet desiredChunks = radiationCoverageChunks(desiredReceivers, desiredMinimumRanges,
                registeredMaximumRange);
        radiationCoverageChunks.clear();
        radiationCoverageChunks.addAll(desiredChunks);

        boolean changed = pruneIrrelevantRadiationOnlySources();
        LongSet chunksToScan = radiationCoverageChunks(receiversToScan, desiredMinimumRanges,
                registeredMaximumRange);
        LongIterator chunkIterator = chunksToScan.iterator();
        while (chunkIterator.hasNext()) {
            long chunkKey = chunkIterator.nextLong();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            if (chunk != null) {
                changed |= scanChunkSources(chunk, false, true);
            }
        }
        if (changed) {
            playerSamples.clear();
            ThermalRadiationSolver.invalidate(level);
        }
    }

    private void registerTransientRadiationReceiver(BlockPos position, long gameTick) {
        registerTransientRadiationReceiver(position, gameTick, 0);
    }

    private void registerTransientRadiationReceiver(BlockPos position, long gameTick, int minimumRange) {
        long key = sectionKey(position);
        long expiry = gameTick + RADIATION_QUERY_RETENTION_TICKS;
        if (minimumRange > 0) {
            transientRadiationDebugReceiverSections.put(key,
                    Math.max(expiry, transientRadiationDebugReceiverSections.getOrDefault(key, Long.MIN_VALUE)));
            if (minimumRange > transientRadiationReceiverMinimumRanges.getOrDefault(key, 0)) {
                transientRadiationReceiverMinimumRanges.put(key, minimumRange);
            }
        } else {
            transientRadiationReceiverSections.put(key,
                    Math.max(expiry, transientRadiationReceiverSections.getOrDefault(key, Long.MIN_VALUE)));
        }
        refreshRadiationCoverage(gameTick);
    }

    private LongSet radiationCoverageChunks(LongSet receivers, Long2IntOpenHashMap minimumRanges,
                                            float registeredMaximumRange) {
        LongSet chunks = new LongOpenHashSet();
        LongIterator iterator = receivers.iterator();
        while (iterator.hasNext()) {
            long receiver = iterator.nextLong();
            int blockPadding = (int) Math.ceil(Math.max(registeredMaximumRange,
                    minimumRanges.getOrDefault(receiver, 0)) + RADIATION_TRACKING_PADDING);
            int minimumBlockX = SectionPos.sectionToBlockCoord(SectionPos.x(receiver)) - blockPadding;
            int maximumBlockX = SectionPos.sectionToBlockCoord(SectionPos.x(receiver)) + 15 + blockPadding;
            int minimumBlockZ = SectionPos.sectionToBlockCoord(SectionPos.z(receiver)) - blockPadding;
            int maximumBlockZ = SectionPos.sectionToBlockCoord(SectionPos.z(receiver)) + 15 + blockPadding;
            int minimumChunkX = SectionPos.blockToSectionCoord(minimumBlockX);
            int maximumChunkX = SectionPos.blockToSectionCoord(maximumBlockX);
            int minimumChunkZ = SectionPos.blockToSectionCoord(minimumBlockZ);
            int maximumChunkZ = SectionPos.blockToSectionCoord(maximumBlockZ);
            for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                    chunks.add(ChunkPos.asLong(chunkX, chunkZ));
                }
            }
        }
        return chunks;
    }

    private boolean scanChunkSources(LevelChunk chunk, boolean includeAirHeating,
                                     boolean includeRadiationOnly) {
        if (!includeAirHeating && !includeRadiationOnly) {
            return false;
        }
        boolean changed = false;
        LevelChunkSection[] chunkSections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < chunkSections.length; sectionIndex++) {
            LevelChunkSection chunkSection = chunkSections[sectionIndex];
            if (chunkSection.hasOnlyAir() || !chunkSection.maybeHas(state -> {
                ThermalSourceRegistry.ThermalSourceDefinition definition =
                        ThermalSourceRegistry.getDefinition(state);
                return definition != null && (includeAirHeating && definition.heatsAir()
                        || includeRadiationOnly && !definition.heatsAir() && definition.radiates());
            })) {
                continue;
            }
            int sectionY = level.getSectionYFromSectionIndex(sectionIndex);
            int baseX = chunk.getPos().getMinBlockX();
            int baseY = SectionPos.sectionToBlockCoord(sectionY);
            int baseZ = chunk.getPos().getMinBlockZ();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = chunkSection.getBlockState(x, y, z);
                        ThermalSourceRegistry.ThermalSourceDefinition definition =
                                ThermalSourceRegistry.getDefinition(state);
                        if (definition == null) {
                            continue;
                        }
                        boolean airHeating = definition.heatsAir();
                        if (airHeating ? !includeAirHeating : !includeRadiationOnly || !definition.radiates()) {
                            continue;
                        }
                        BlockPos position = new BlockPos(baseX + x, baseY + y, baseZ + z);
                        if (!airHeating && !isRadiationSourceRelevant(position, definition)) {
                            continue;
                        }
                        refreshSourceAt(position);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean pruneIrrelevantRadiationOnlySources() {
        boolean changed = false;
        var iterator = sources.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            SourceRecord record = iterator.next().getValue();
            if (record.definition().heatsAir()
                    || isRadiationSourceRelevant(record.position(), record.definition())) {
                continue;
            }
            removeFaces(record);
            removeSourcePosition(record.position());
            iterator.remove();
            changed = true;
        }
        return changed;
    }

    private boolean isRadiationSourceRelevant(BlockPos source,
                                               ThermalSourceRegistry.ThermalSourceDefinition definition) {
        if (!definition.radiates() || radiationReceiverSections.isEmpty()) {
            return false;
        }
        double sourceX = source.getX() + 0.5D;
        double sourceY = source.getY() + 0.5D;
        double sourceZ = source.getZ() + 0.5D;
        LongIterator iterator = radiationReceiverSections.iterator();
        while (iterator.hasNext()) {
            long receiver = iterator.nextLong();
            double range = Math.max(definition.maximumNaturalRadiationRange(),
                    radiationReceiverMinimumRanges.getOrDefault(receiver, 0)) + RADIATION_TRACKING_PADDING;
            double rangeSquared = range * range;
            double minimumX = SectionPos.sectionToBlockCoord(SectionPos.x(receiver));
            double minimumY = SectionPos.sectionToBlockCoord(SectionPos.y(receiver));
            double minimumZ = SectionPos.sectionToBlockCoord(SectionPos.z(receiver));
            double deltaX = distanceOutside(sourceX, minimumX, minimumX + 16.0D);
            double deltaY = distanceOutside(sourceY, minimumY, minimumY + 16.0D);
            double deltaZ = distanceOutside(sourceZ, minimumZ, minimumZ + 16.0D);
            if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= rangeSquared) {
                return true;
            }
        }
        return false;
    }

    private static double distanceOutside(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return value > maximum ? value - maximum : 0.0D;
    }

    public void unloadChunk(ChunkPos chunkPos) {
        invalidateSimulation();
        pendingChunkLoads.remove(chunkPos.toLong());
        playerSamples.clear();
        clearBoundaryContactCache();
        pendingUnloadedChunkData.put(chunkPos.toLong(), serializeChunk(chunkPos));
        var sourceIterator = sources.long2ObjectEntrySet().iterator();
        while (sourceIterator.hasNext()) {
            Long2ObjectMap.Entry<SourceRecord> entry = sourceIterator.next();
            BlockPos position = BlockPos.of(entry.getLongKey());
            if (SectionPos.blockToSectionCoord(position.getX()) == chunkPos.x
                    && SectionPos.blockToSectionCoord(position.getZ()) == chunkPos.z) {
                removeFaces(entry.getValue());
                if (entry.getValue().definition().heatsAir()) {
                    decrementSectionSourceCount(entry.getValue().position());
                }
                sourceIterator.remove();
            }
        }
        sourcePositionsByChunk.remove(chunkPos.toLong());
        sections.entrySet().removeIf(entry ->
                SectionPos.x(entry.getKey()) == chunkPos.x && SectionPos.z(entry.getKey()) == chunkPos.z);
        ThermalRadiationSolver.invalidate(level);
    }

    public CompoundTag saveChunk(ChunkPos chunkPos) {
        CompoundTag pending = pendingUnloadedChunkData.remove(chunkPos.toLong());
        return pending != null ? pending : serializeChunk(chunkPos);
    }

    private CompoundTag serializeChunk(ChunkPos chunkPos) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", DATA_VERSION);
        ListTag sectionTags = new ListTag();
        for (ThermalSection section : sections.values()) {
            if (SectionPos.x(section.sectionKey) != chunkPos.x || SectionPos.z(section.sectionKey) != chunkPos.z) {
                continue;
            }
            float hiddenCutoff = ThermalConfig.hiddenTemperatureCutoff();
            int[] packed = packTemperatures(section, hiddenCutoff);
            int[] hiddenPacked = packHiddenTemperatures(section, hiddenCutoff);
            if (packed.length == 0 && hiddenPacked.length == 0) {
                continue;
            }
            CompoundTag sectionTag = new CompoundTag();
            sectionTag.putInt("Y", SectionPos.y(section.sectionKey));
            sectionTag.put("Cells", new IntArrayTag(packed));
            sectionTag.put("HiddenCells", new IntArrayTag(hiddenPacked));
            sectionTags.add(sectionTag);
        }
        root.put("Sections", sectionTags);
        return root;
    }

    public void loadChunkData(ChunkPos chunkPos, CompoundTag root) {
        invalidateSimulation();
        CompoundTag pending = pendingUnloadedChunkData.remove(chunkPos.toLong());
        if (pending != null) {
            root = pending;
        }
        int version = root.getInt("Version");
        if (version != 3 && version != DATA_VERSION) {
            return;
        }
        ListTag sectionTags = root.getList("Sections", Tag.TAG_COMPOUND);
        long currentTick = level.getGameTime();
        float visibleCutoff = ThermalConfig.airTemperatureCutoff();
        float hiddenCutoff = ThermalConfig.hiddenTemperatureCutoff();
        for (Tag rawTag : sectionTags) {
            CompoundTag sectionTag = (CompoundTag) rawTag;
            int sectionY = sectionTag.getInt("Y");
            if (sectionY < level.getMinSection() || sectionY >= level.getMaxSection()) {
                continue;
            }
            long key = SectionPos.asLong(chunkPos.x, sectionY, chunkPos.z);
            ThermalSection section = sections.computeIfAbsent(key, ignored -> new ThermalSection(key, currentTick));
            java.util.Arrays.fill(section.current, 0.0F);
            section.clearHiddenTemperatures();
            section.active.clear();
            section.clearAirCache();
            section.boundaryCacheKnown.clear();
            int[] packed = sectionTag.getIntArray("Cells");
            for (int value : packed) {
                int index = value & 0xFFFF;
                if (index >= ThermalSection.VOLUME) {
                    continue;
                }
                short quantized = (short) (value >>> 16);
                float temperature = quantized / 10.0F;
                loadTemperature(section, index, temperature, visibleCutoff, hiddenCutoff);
            }
            if (version >= 4) {
                int[] hiddenPacked = sectionTag.getIntArray("HiddenCells");
                for (int packedIndex = 0; packedIndex + 1 < hiddenPacked.length; packedIndex += 2) {
                    int index = hiddenPacked[packedIndex];
                    if (index < 0 || index >= ThermalSection.VOLUME) {
                        continue;
                    }
                    float temperature = hiddenPacked[packedIndex + 1] / 100.0F;
                    loadTemperature(section, index, temperature, visibleCutoff, hiddenCutoff);
                }
            }
            section.forceDue = !section.active.isEmpty();
        }
        for (long targetKey : heatingFacesByTarget.keySet()) {
            BlockPos target = BlockPos.of(targetKey);
            if (SectionPos.blockToSectionCoord(target.getX()) == chunkPos.x
                    && SectionPos.blockToSectionCoord(target.getZ()) == chunkPos.z) {
                markActive(target);
            }
        }
    }

    public Map<Long, Float> snapshot(BlockPos center, int radius) {
        Map<Long, Float> result = new HashMap<>();
        int radiusSquared = radius * radius;
        int minimumSectionX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maximumSectionX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minimumSectionY = SectionPos.blockToSectionCoord(center.getY() - radius);
        int maximumSectionY = SectionPos.blockToSectionCoord(center.getY() + radius);
        int minimumSectionZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maximumSectionZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        for (int sectionX = minimumSectionX; sectionX <= maximumSectionX; sectionX++) {
            for (int sectionY = minimumSectionY; sectionY <= maximumSectionY; sectionY++) {
                for (int sectionZ = minimumSectionZ; sectionZ <= maximumSectionZ; sectionZ++) {
                    ThermalSection section = sections.get(SectionPos.asLong(sectionX, sectionY, sectionZ));
                    if (section == null) {
                        continue;
                    }
                    int baseX = SectionPos.sectionToBlockCoord(sectionX);
                    int baseY = SectionPos.sectionToBlockCoord(sectionY);
                    int baseZ = SectionPos.sectionToBlockCoord(sectionZ);
                    for (int index = 0; index < ThermalSection.VOLUME; index++) {
                        float temperature = section.current[index];
                        if (Math.abs(temperature) < SAVE_THRESHOLD) {
                            continue;
                        }
                        BlockPos position = positionAt(baseX, baseY, baseZ, index);
                        if (position.distSqr(center) <= radiusSquared) {
                            result.put(position.asLong(), temperature);
                        }
                    }
                }
            }
        }
        return result;
    }

    /** Hidden solver values are exposed only through the explicit creative debug request path. */
    public Map<Long, Float> hiddenSnapshot(BlockPos center, int radius) {
        Map<Long, Float> result = new HashMap<>();
        int radiusSquared = radius * radius;
        float hiddenCutoff = ThermalConfig.hiddenTemperatureCutoff();
        int minimumSectionX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maximumSectionX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minimumSectionY = SectionPos.blockToSectionCoord(center.getY() - radius);
        int maximumSectionY = SectionPos.blockToSectionCoord(center.getY() + radius);
        int minimumSectionZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maximumSectionZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        for (int sectionX = minimumSectionX; sectionX <= maximumSectionX; sectionX++) {
            for (int sectionY = minimumSectionY; sectionY <= maximumSectionY; sectionY++) {
                for (int sectionZ = minimumSectionZ; sectionZ <= maximumSectionZ; sectionZ++) {
                    ThermalSection section = sections.get(SectionPos.asLong(sectionX, sectionY, sectionZ));
                    if (section == null || !section.hasHiddenTemperatures()) {
                        continue;
                    }
                    int baseX = SectionPos.sectionToBlockCoord(sectionX);
                    int baseY = SectionPos.sectionToBlockCoord(sectionY);
                    int baseZ = SectionPos.sectionToBlockCoord(sectionZ);
                    for (int index = 0; index < ThermalSection.VOLUME; index++) {
                        float temperature = section.getHiddenTemperature(index);
                        if (Math.abs(temperature) < hiddenCutoff) {
                            continue;
                        }
                        BlockPos position = positionAt(baseX, baseY, baseZ, index);
                        if (position.distSqr(center) <= radiusSquared) {
                            result.put(position.asLong(), temperature);
                        }
                    }
                }
            }
        }
        return result;
    }

    public List<SourceDebug> sourceSnapshot(BlockPos center, int radius, int limit) {
        registerTransientRadiationReceiver(center, level.getGameTime(), radius);
        List<SourceDebug> result = new ArrayList<>();
        for (SourceRecord source : sourcesNear(center, radius)) {
            if (source.active()) {
                result.add(new SourceDebug(source.position().asLong(), source.surfaceTemperature()));
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    public List<SurfaceDebug> surfaceSnapshot(BlockPos center, int radius, int limit) {
        registerTransientRadiationReceiver(center, level.getGameTime(), radius);
        List<SurfaceDebug> result = new ArrayList<>();
        for (SourceRecord source : sourcesNear(center, radius)) {
            if (!source.active()) {
                continue;
            }
            for (ThermalFace face : source.faces()) {
                result.add(new SurfaceDebug(face.source().asLong(), face.target().asLong(),
                        (byte) face.direction().ordinal(), face.surfaceTemperature(), face.area()));
                if (result.size() >= limit) {
                    return result;
                }
            }
        }
        return result;
    }

    private List<SourceRecord> sourcesNear(BlockPos center, int radius) {
        List<SourceRecord> result = new ArrayList<>();
        int radiusSquared = radius * radius;
        int minimumChunkX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maximumChunkX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minimumChunkZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maximumChunkZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                LongOpenHashSet positions = sourcePositionsByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
                if (positions == null) {
                    continue;
                }
                LongIterator positionIterator = positions.iterator();
                while (positionIterator.hasNext()) {
                    long positionKey = positionIterator.nextLong();
                    SourceRecord source = sources.get(positionKey);
                    if (source != null && source.position().distSqr(center) <= radiusSquared) {
                        result.add(source);
                    }
                }
            }
        }
        return result;
    }

    public ThermalDiagnostics diagnostics() {
        rebuildRadiantPatchesIfNeeded();
        int activeCells = 0;
        int exposedFaces = 0;
        int patches = 0;
        for (ThermalSection section : sections.values()) {
            activeCells += section.active.cardinality();
        }
        for (SourceRecord source : sources.values()) {
            exposedFaces += source.faces().size();
        }
        for (List<RadiantPatch> sectionPatches : radiantPatchesBySection.values()) {
            patches += sectionPatches.size();
        }
        ThermalRadiationSolver.RadiationDiagnostics radiation = ThermalRadiationSolver.diagnostics(level);
        return new ThermalDiagnostics(sections.size(), sources.size(), activeCells, exposedFaces, patches,
                lastProcessedCells, lastDeferredSections, radiation.raysThisTick(), radiation.totalRays(),
                radiation.cacheHits(), radiation.staleCacheUses(), radiation.deferredRays());
    }

    public void removePlayer(ServerPlayer player) {
        playerSamples.remove(player.getUUID());
    }

    List<RadiantPatch> radiantPatchesNear(BlockPos target) {
        rebuildRadiantPatchesIfNeeded();
        float maximumRange = maximumLoadedRadiationRange;
        int sectionRadius = Math.max(0, (int) Math.ceil(maximumRange / 16.0F) + 1);
        int centerX = SectionPos.blockToSectionCoord(target.getX());
        int centerY = SectionPos.blockToSectionCoord(target.getY());
        int centerZ = SectionPos.blockToSectionCoord(target.getZ());
        List<RadiantPatch> result = new ArrayList<>();
        long side = sectionRadius * 2L + 1L;
        long cubeSections = side > 2_000_000L ? Long.MAX_VALUE : side * side * side;
        long indexedThreshold = Math.max(125L, radiantPatchesBySection.size() * 8L);
        if (cubeSections <= indexedThreshold) {
            for (int x = centerX - sectionRadius; x <= centerX + sectionRadius; x++) {
                for (int y = centerY - sectionRadius; y <= centerY + sectionRadius; y++) {
                    for (int z = centerZ - sectionRadius; z <= centerZ + sectionRadius; z++) {
                        List<RadiantPatch> patches = radiantPatchesBySection.get(SectionPos.asLong(x, y, z));
                        if (patches != null) {
                            addPatchesInNaturalRange(result, patches, target);
                        }
                    }
                }
            }
        } else {
            for (Map.Entry<Long, List<RadiantPatch>> entry : radiantPatchesBySection.entrySet()) {
                long key = entry.getKey();
                if (Math.abs((long) SectionPos.x(key) - centerX) <= sectionRadius
                        && Math.abs((long) SectionPos.y(key) - centerY) <= sectionRadius
                        && Math.abs((long) SectionPos.z(key) - centerZ) <= sectionRadius) {
                    addPatchesInNaturalRange(result, entry.getValue(), target);
                }
            }
        }
        return result;
    }

    private static void addPatchesInNaturalRange(List<RadiantPatch> result, List<RadiantPatch> patches,
                                                  BlockPos target) {
        Vec3 targetCenter = Vec3.atCenterOf(target);
        for (RadiantPatch patch : patches) {
            Float decay = patch.radiationDecayPerBlock();
            if (decay == null) {
                continue;
            }
            double naturalRange = Math.abs(patch.radiationTemperature()) / decay;
            double patchExtent = Math.sqrt(patch.area()) * 0.5D;
            double queryRange = naturalRange + patchExtent + 1.0D;
            if (patch.distanceToSqr(targetCenter) <= queryRange * queryRange) {
                result.add(patch);
            }
        }
    }

    private List<BoundaryContact> buildBoundaryContacts(BlockPos position) {
        List<BoundaryContact> contacts = new ArrayList<>();
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbor = position.relative(direction);
            if (!level.hasChunkAt(neighbor) || isAirMedium(neighbor)) {
                continue;
            }
            BlockState state = level.getBlockState(neighbor);
            float area = solidContactArea(neighbor, state, direction.getOpposite());
            if (area > 0.0F) {
                contacts.add(new BoundaryContact(neighbor.immutable(), state, area));
            }
        }
        return List.copyOf(contacts);
    }

    private void invalidateBoundaryContacts(BlockPos changedPosition) {
        invalidateBoundaryContact(changedPosition);
        for (Direction direction : DIRECTIONS) {
            invalidateBoundaryContact(changedPosition.relative(direction));
        }
    }

    private void clearBoundaryContactCache() {
        boundaryContactCache.clear();
        for (ThermalSection section : sections.values()) {
            section.boundaryCacheKnown.clear();
        }
    }

    private void invalidateBoundaryContact(BlockPos airPosition) {
        boundaryContactCache.remove(airPosition.asLong());
        ThermalSection section = sections.get(sectionKey(airPosition));
        if (section != null) {
            section.boundaryCacheKnown.clear(localIndex(airPosition));
        }
    }

    private float solidContactArea(BlockPos solidPosition, BlockState state, Direction faceTowardAir) {
        VoxelShape collisionShape = state.getCollisionShape(level, solidPosition);
        if (collisionShape.isEmpty()) {
            return 0.0F;
        }
        List<FaceRectangle> rectangles = new ArrayList<>();
        for (AABB box : collisionShape.toAabbs()) {
            boolean touchesFace = switch (faceTowardAir) {
                case WEST -> box.minX <= 1.0E-7D;
                case EAST -> box.maxX >= 1.0D - 1.0E-7D;
                case DOWN -> box.minY <= 1.0E-7D;
                case UP -> box.maxY >= 1.0D - 1.0E-7D;
                case NORTH -> box.minZ <= 1.0E-7D;
                case SOUTH -> box.maxZ >= 1.0D - 1.0E-7D;
            };
            if (!touchesFace) {
                continue;
            }
            rectangles.add(switch (faceTowardAir.getAxis()) {
                case X -> new FaceRectangle(box.minY, box.maxY, box.minZ, box.maxZ);
                case Y -> new FaceRectangle(box.minX, box.maxX, box.minZ, box.maxZ);
                case Z -> new FaceRectangle(box.minX, box.maxX, box.minY, box.maxY);
            });
        }
        double area = rectangleUnionArea(rectangles);
        return (float) Math.max(0.0D, Math.min(1.0D, area));
    }

    private static double rectangleUnionArea(List<FaceRectangle> rectangles) {
        if (rectangles.isEmpty()) {
            return 0.0D;
        }
        List<Double> coordinates = new ArrayList<>(rectangles.size() * 2);
        for (FaceRectangle rectangle : rectangles) {
            coordinates.add(rectangle.minimumFirst());
            coordinates.add(rectangle.maximumFirst());
        }
        coordinates.sort(Double::compare);
        double area = 0.0D;
        for (int index = 0; index + 1 < coordinates.size(); index++) {
            double minimum = coordinates.get(index);
            double maximum = coordinates.get(index + 1);
            if (maximum - minimum <= 1.0E-9D) {
                continue;
            }
            double midpoint = (minimum + maximum) * 0.5D;
            List<double[]> intervals = new ArrayList<>();
            for (FaceRectangle rectangle : rectangles) {
                if (rectangle.minimumFirst() <= midpoint && rectangle.maximumFirst() >= midpoint) {
                    intervals.add(new double[]{rectangle.minimumSecond(), rectangle.maximumSecond()});
                }
            }
            intervals.sort(Comparator.comparingDouble(interval -> interval[0]));
            double covered = 0.0D;
            double intervalStart = Double.NaN;
            double intervalEnd = Double.NaN;
            for (double[] interval : intervals) {
                if (Double.isNaN(intervalStart)) {
                    intervalStart = interval[0];
                    intervalEnd = interval[1];
                } else if (interval[0] <= intervalEnd) {
                    intervalEnd = Math.max(intervalEnd, interval[1]);
                } else {
                    covered += intervalEnd - intervalStart;
                    intervalStart = interval[0];
                    intervalEnd = interval[1];
                }
            }
            if (!Double.isNaN(intervalStart)) {
                covered += intervalEnd - intervalStart;
            }
            area += (maximum - minimum) * covered;
        }
        return area;
    }

    private void refreshSourceAt(BlockPos position) {
        long positionKey = position.asLong();
        SourceRecord previous = sources.remove(positionKey);
        if (previous != null) {
            removeFaces(previous);
            removeSourcePosition(previous.position());
            if (previous.definition().heatsAir()) {
                decrementSectionSourceCount(previous.position());
            }
        }
        if (!level.hasChunkAt(position)) {
            return;
        }
        BlockState state = level.getBlockState(position);
        ThermalSourceRegistry.ThermalSourceDefinition configuredDefinition =
                ThermalSourceRegistry.getDefinition(state);
        if (configuredDefinition == null
                || !configuredDefinition.heatsAir() && (!configuredDefinition.radiates()
                || !isRadiationSourceRelevant(position, configuredDefinition))) {
            updateSectionSourceFlag(sectionKey(position));
            return;
        }
        ThermalSourceRegistry.ResolvedThermalSource resolved = ThermalSourceRegistry.resolve(level, position, state);
        if (resolved == null) {
            updateSectionSourceFlag(sectionKey(position));
            return;
        }
        ThermalSourceRegistry.ThermalSourceDefinition definition = resolved.definition();
        boolean active = resolved.active();
        float surfaceTemperature = resolved.surfaceTemperature();
        Float radiationTemperature = resolved.radiationTemperature();
        boolean heatsAir = definition.heatsAir();
        boolean radiates = radiationTemperature != null && Math.abs(radiationTemperature) >= 0.1F;
        List<ThermalFace> faces = new ArrayList<>();
        if (active && (heatsAir || radiates)) {
            for (Direction direction : DIRECTIONS) {
                BlockPos target = position.relative(direction);
                if (!isLoadedAirMedium(target)) {
                    continue;
                }
                ThermalFace face = new ThermalFace(position.immutable(), target.immutable(), direction,
                        surfaceTemperature, definition.faceHeatingRate(), radiationTemperature,
                        definition.radiationDecayPerBlock(), 1.0F);
                faces.add(face);
                if (heatsAir) {
                    heatingFacesByTarget.computeIfAbsent(target.asLong(), ignored -> new ArrayList<>()).add(face);
                    wakeAround(target);
                }
                if (radiates) {
                    radiantFacesBySection.computeIfAbsent(sectionKey(position), ignored -> new ArrayList<>()).add(face);
                    radiantPatchesDirty = true;
                }
            }
        }
        // Constant radiation-only sources with no exposed air face cannot contribute anything.
        // Neighbor block changes refresh this position again if it becomes exposed; dynamic providers
        // stay indexed so their low-frequency active/temperature polling remains intact.
        if (!heatsAir && !resolved.dynamic() && faces.isEmpty()) {
            updateSectionSourceFlag(sectionKey(position));
            return;
        }
        SourceRecord record = new SourceRecord(position.immutable(), definition, active, surfaceTemperature,
                resolved.dynamic(), List.copyOf(faces));
        sources.put(positionKey, record);
        if (definition.heatsAir()) {
            incrementSectionSourceCount(position);
        }
        sourcePositionsByChunk.computeIfAbsent(new ChunkPos(position).toLong(), ignored -> new LongOpenHashSet())
                .add(positionKey);
    }

    private void pollDynamicSources() {
        List<BlockPos> changed = new ArrayList<>();
        for (SourceRecord record : List.copyOf(sources.values())) {
            if (!record.dynamic()) {
                continue;
            }
            BlockState state = level.getBlockState(record.position());
            ThermalSourceRegistry.ResolvedThermalSource resolved =
                    ThermalSourceRegistry.resolve(level, record.position(), state);
            boolean active = resolved != null && resolved.active();
            float temperature = resolved == null ? 0.0F : resolved.surfaceTemperature();
            if (active != record.active() || Math.abs(temperature - record.surfaceTemperature()) >= WAKE_THRESHOLD) {
                changed.add(record.position());
            }
        }
        if (!changed.isEmpty()) {
            invalidateSimulation();
            for (BlockPos position : changed) {
                refreshSourceAt(position);
                wakeAround(position);
            }
            playerSamples.clear();
            ThermalRadiationSolver.invalidate(level);
        }
    }

    public void refreshChunkBorders(ChunkPos chunkPos) {
        invalidateSimulation();
        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();
        long[] neighboringChunks = {
                ChunkPos.asLong(chunkPos.x - 1, chunkPos.z),
                ChunkPos.asLong(chunkPos.x + 1, chunkPos.z),
                ChunkPos.asLong(chunkPos.x, chunkPos.z - 1),
                ChunkPos.asLong(chunkPos.x, chunkPos.z + 1)
        };
        for (long neighboringChunk : neighboringChunks) {
            LongOpenHashSet positions = sourcePositionsByChunk.get(neighboringChunk);
            if (positions == null) {
                continue;
            }
            for (long packedPosition : positions.toLongArray()) {
                BlockPos position = BlockPos.of(packedPosition);
                if (position.getX() == minX - 1 || position.getX() == maxX + 1
                        || position.getZ() == minZ - 1 || position.getZ() == maxZ + 1) {
                    refreshSourceAt(position);
                }
            }
        }
    }

    private void removeSourcePosition(BlockPos position) {
        long chunkKey = new ChunkPos(position).toLong();
        LongOpenHashSet positions = sourcePositionsByChunk.get(chunkKey);
        if (positions == null) {
            return;
        }
        positions.remove(position.asLong());
        if (positions.isEmpty()) {
            sourcePositionsByChunk.remove(chunkKey);
        }
    }

    private void removeFaces(SourceRecord record) {
        for (ThermalFace face : record.faces()) {
            if (face.faceHeatingRate() > 0.0F) {
                removeFace(heatingFacesByTarget, face.target().asLong(), face);
                markActive(face.target());
            }
            if (face.radiationTemperature() != null && Math.abs(face.radiationTemperature()) >= 0.1F) {
                removeFace(radiantFacesBySection, sectionKey(face.source()), face);
                radiantPatchesDirty = true;
            }
        }
    }

    private static void removeFace(Map<Long, List<ThermalFace>> index, long key, ThermalFace face) {
        List<ThermalFace> faces = index.get(key);
        if (faces == null) {
            return;
        }
        faces.remove(face);
        if (faces.isEmpty()) {
            index.remove(key);
        }
    }

    private void updateSectionSourceFlag(long key) {
        ThermalSection section = sections.get(key);
        if (section == null) {
            return;
        }
        section.hasSource = sourceCountsBySection.getOrDefault(key, 0) > 0;
    }

    private void incrementSectionSourceCount(BlockPos position) {
        long key = sectionKey(position);
        sourceCountsBySection.merge(key, 1, Integer::sum);
        ensureSection(position).hasSource = true;
    }

    private void decrementSectionSourceCount(BlockPos position) {
        long key = sectionKey(position);
        sourceCountsBySection.computeIfPresent(key, (ignored, count) -> count <= 1 ? null : count - 1);
        updateSectionSourceFlag(key);
    }

    private void wakeAround(BlockPos position) {
        markActive(position);
        for (Direction direction : DIRECTIONS) {
            markActive(position.relative(direction));
        }
    }

    private void markActive(BlockPos position) {
        if (isLoadedAirMedium(position)) {
            ensureSection(position).wake(localIndex(position));
        }
    }

    private boolean isNearPlayer(ThermalSection section) {
        int sectionChunkX = SectionPos.x(section.sectionKey);
        int sectionChunkZ = SectionPos.z(section.sectionKey);
        for (ServerPlayer player : level.players()) {
            ChunkPos playerChunk = player.chunkPosition();
            if (Math.abs(playerChunk.x - sectionChunkX) <= ACTIVE_CHUNK_RADIUS
                    && Math.abs(playerChunk.z - sectionChunkZ) <= ACTIVE_CHUNK_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private void wakeStoredTemperatures(ThermalSection section) {
        boolean found = false;
        for (int index = 0; index < section.current.length; index++) {
            if (Math.abs(section.current[index]) >= 0.0001F
                    || Math.abs(section.getHiddenTemperature(index)) >= 0.0001F) {
                section.active.set(index);
                found = true;
            }
        }
        if (found) {
            section.stableCycles = 0;
            section.forceDue = true;
        }
    }

    /** Low-frequency cleanup for stale representation or block-state changes missed by events. */
    private void safetySweepInvalidTemperatures(ThermalSection section, long gameTick,
                                                float visibleCutoff, float hiddenCutoff) {
        if (gameTick % RESIDUAL_SWEEP_INTERVAL
                != Math.floorMod(Long.hashCode(section.sectionKey), RESIDUAL_SWEEP_INTERVAL)) {
            return;
        }
        int baseX = SectionPos.sectionToBlockCoord(SectionPos.x(section.sectionKey));
        int baseY = SectionPos.sectionToBlockCoord(SectionPos.y(section.sectionKey));
        int baseZ = SectionPos.sectionToBlockCoord(SectionPos.z(section.sectionKey));
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int index = 0; index < section.current.length; index++) {
            float visible = section.current[index];
            float hidden = section.getHiddenTemperature(index);
            if (visible == 0.0F && hidden == 0.0F) {
                continue;
            }
            position.set(baseX + (index & 15), baseY + ((index >>> 8) & 15),
                    baseZ + ((index >>> 4) & 15));
            boolean invalidVisible = visible != 0.0F && (Math.abs(visible) < hiddenCutoff
                    || visibleCutoff > 0.0F && Math.abs(visible) < visibleCutoff);
            boolean invalidHidden = hidden != 0.0F && (Math.abs(hidden) < hiddenCutoff
                    || visibleCutoff <= 0.0F || Math.abs(hidden) >= visibleCutoff);
            boolean loadedAir = isLoadedAirMedium(position);
            if (!loadedAir) {
                section.invalidateAirCache(index);
            }
            if (!loadedAir || visible != 0.0F && hidden != 0.0F
                    || invalidVisible || invalidHidden) {
                section.wake(index);
            }
        }
    }

    private Scheduling schedulingFor(ThermalSection section, long gameTick, boolean nearPlayer) {
        if (nearPlayer) {
            section.lastNearTick = gameTick;
            return new Scheduling(NEAR_INTERVAL, 4);
        }
        if (section.lastNearTick != Long.MIN_VALUE && gameTick - section.lastNearTick <= RECENT_GRACE_TICKS) {
            return new Scheduling(RECENT_INTERVAL, 2);
        }
        return new Scheduling(IDLE_INTERVAL, 1);
    }

    private void pruneEmptySections(Set<Long> protectedSections) {
        Set<Long> removed = new HashSet<>();
        sections.entrySet().removeIf(entry -> {
            ThermalSection section = entry.getValue();
            boolean remove = !protectedSections.contains(entry.getKey())
                    && !section.hasSource && section.active.isEmpty()
                    && !section.hasSavedTemperature(ThermalConfig.hiddenTemperatureCutoff())
                    && !section.hasHiddenTemperatures();
            if (remove) {
                removed.add(entry.getKey());
            }
            return remove;
        });
        if (!removed.isEmpty()) {
            boundaryContactCache.entrySet().removeIf(entry ->
                    removed.contains(sectionKey(BlockPos.of(entry.getKey()))));
        }
    }

    /** Source boundaries never also behave as passive walls, including radiation-only faces. */
    private boolean isActiveSourceFace(BlockPos neighbor) {
        SourceRecord source = sources.get(neighbor.asLong());
        return source != null && source.active()
                && (source.definition().heatsAir() || source.definition().radiates());
    }

    private boolean isLoadedAirMedium(BlockPos position) {
        return !level.isOutsideBuildHeight(position) && level.hasChunkAt(position) && isAirMedium(position);
    }

    private boolean isAirMedium(BlockPos position) {
        if (level.isOutsideBuildHeight(position)) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        return state.getFluidState().isEmpty()
                && !ThermalSourceRegistry.isRegisteredBlock(state)
                && (state.isAir() || state.getCollisionShape(level, position).isEmpty());
    }

    private ThermalSection ensureSection(BlockPos position) {
        long key = sectionKey(position);
        return sections.computeIfAbsent(key, ignored -> new ThermalSection(key, level.getGameTime()));
    }

    private void markChunkUnsaved(ThermalSection section) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.x(section.sectionKey),
                SectionPos.z(section.sectionKey));
        if (chunk != null) {
            chunk.setUnsaved(true);
        }
    }

    private static long sectionKey(BlockPos position) {
        return SectionPos.asLong(SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getY()), SectionPos.blockToSectionCoord(position.getZ()));
    }

    private static int localIndex(BlockPos position) {
        return SectionPos.sectionRelative(position.getX())
                | (SectionPos.sectionRelative(position.getZ()) << 4)
                | (SectionPos.sectionRelative(position.getY()) << 8);
    }

    private static BlockPos positionAt(int baseX, int baseY, int baseZ, int index) {
        return new BlockPos(baseX + (index & 15), baseY + ((index >>> 8) & 15),
                baseZ + ((index >>> 4) & 15));
    }

    private static BlockPos positionAt(long sectionKey, int index) {
        return positionAt(SectionPos.sectionToBlockCoord(SectionPos.x(sectionKey)),
                SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey)),
                SectionPos.sectionToBlockCoord(SectionPos.z(sectionKey)), index);
    }

    private static void loadTemperature(ThermalSection section, int index, float temperature,
                                        float visibleCutoff, float hiddenCutoff) {
        if (Math.abs(temperature) < hiddenCutoff
                || Math.abs(section.getSolverTemperature(index)) >= Math.abs(temperature)) {
            return;
        }
        if (visibleCutoff <= 0.0F || Math.abs(temperature) >= visibleCutoff) {
            section.current[index] = temperature;
            section.clearHiddenTemperature(index);
        } else {
            section.setHiddenTemperature(index, temperature);
        }
        section.active.set(index);
    }

    private static int[] packTemperatures(ThermalSection section, float storageThreshold) {
        int count = 0;
        for (float temperature : section.current) {
            if (Math.abs(temperature) >= storageThreshold) {
                count++;
            }
        }
        int[] packed = new int[count];
        int output = 0;
        for (int index = 0; index < section.current.length; index++) {
            float temperature = section.current[index];
            if (Math.abs(temperature) < storageThreshold) {
                continue;
            }
            int rounded = Math.round(temperature * 10.0F);
            short quantized = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, rounded));
            packed[output++] = ((quantized & 0xFFFF) << 16) | index;
        }
        return packed;
    }

    /** Hidden values use index/value pairs so 0.01 WTU precision still covers the full float range. */
    private static int[] packHiddenTemperatures(ThermalSection section, float hiddenCutoff) {
        int count = 0;
        for (int index = 0; index < ThermalSection.VOLUME; index++) {
            if (Math.abs(section.getHiddenTemperature(index)) >= hiddenCutoff) {
                count++;
            }
        }
        int[] packed = new int[count * 2];
        int output = 0;
        for (int index = 0; index < ThermalSection.VOLUME; index++) {
            float temperature = section.getHiddenTemperature(index);
            if (Math.abs(temperature) < hiddenCutoff) {
                continue;
            }
            packed[output++] = index;
            packed[output++] = Math.round(temperature * 100.0F);
        }
        return packed;
    }

    public record ThermalSample(float airTemperature, float radiationOffset, float effectiveTemperature) {
    }

    public record ThermalFace(BlockPos source, BlockPos target, Direction direction, float surfaceTemperature,
                              float faceHeatingRate, @Nullable Float radiationTemperature,
                              @Nullable Float radiationDecayPerBlock, float area) {
    }

    public record RadiantPatch(Vec3 center, Direction direction, float radiationTemperature,
                               Float radiationDecayPerBlock, float area, long identity, AABB bounds) {

        public Vec3 closestPoint(Vec3 point) {
            return new Vec3(clamp(point.x, bounds.minX, bounds.maxX),
                    clamp(point.y, bounds.minY, bounds.maxY),
                    clamp(point.z, bounds.minZ, bounds.maxZ));
        }

        public double distanceToSqr(Vec3 point) {
            Vec3 closest = closestPoint(point);
            return closest.distanceToSqr(point);
        }

        private static double clamp(double value, double minimum, double maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }
    }

    public record SourceDebug(long position, float temperature) {
    }

    public record SurfaceDebug(long sourcePosition, long targetPosition, byte direction,
                               float temperature, float area) {
    }

    public record ThermalDiagnostics(int sectionCount, int sourceCount, int activeCellCount,
                                     int exposedFaceCount, int radiantPatchCount, int processedCellsLastTick,
                                     int deferredSectionsLastTick, int raysThisTick, long totalRays,
                                     long radiationCacheHits, long staleRadiationCacheUses,
                                     long deferredRays) {
    }

    private record SourceRecord(BlockPos position, ThermalSourceRegistry.ThermalSourceDefinition definition,
                                boolean active, float surfaceTemperature, boolean dynamic,
                                List<ThermalFace> faces) {
    }

    private record Scheduling(int intervalTicks, int maximumSubsteps) {
    }

    private record SimulationJob(ThermalSection section, int substeps, float stepSeconds,
                                 int estimatedWork, double urgency) {
    }

    private record CachedPlayerSample(long tick, ThermalSample sample) {
    }

    private record FaceRectangle(double minimumFirst, double maximumFirst,
                                 double minimumSecond, double maximumSecond) {
    }

    private record BoundaryContact(BlockPos solidPosition, BlockState solidState, float area) {
    }

    private void rebuildRadiantPatchesIfNeeded() {
        if (!radiantPatchesDirty) {
            return;
        }
        radiantPatchesBySection.clear();
        maximumLoadedRadiationRange = 0.0F;
        List<ThermalFace> sorted = new ArrayList<>();
        for (List<ThermalFace> faces : radiantFacesBySection.values()) {
            sorted.addAll(faces);
        }
        sorted.sort(Comparator
                .comparingInt((ThermalFace face) -> face.direction().ordinal())
                .thenComparingInt(this::facePlane)
                .thenComparingInt(this::firstPatchCoordinate)
                .thenComparingInt(this::secondPatchCoordinate)
                .thenComparingInt(face -> face.radiationTemperature() == null
                        ? 0 : Float.floatToIntBits(face.radiationTemperature()))
                .thenComparingInt(face -> face.radiationDecayPerBlock() == null
                        ? 0 : Float.floatToIntBits(face.radiationDecayPerBlock())));
        Map<MergeKey, PatchMergeGroup> groups = new LinkedHashMap<>();
        for (ThermalFace face : sorted) {
            groups.computeIfAbsent(mergeKey(face), ignored -> new PatchMergeGroup()).add(face,
                    firstPatchCoordinate(face), secondPatchCoordinate(face));
        }
        for (PatchMergeGroup group : groups.values()) {
            GreedyPatchMerger.merge(group.orderedFaces, this::firstPatchCoordinate,
                    this::secondPatchCoordinate, MAX_PATCH_SPAN, group,
                    (ignoredFirst, firstCoordinate, secondCoordinate, width, height, members) -> {
                ThermalFace first = members.get(0);
                double x = 0.0D;
                double y = 0.0D;
                double z = 0.0D;
                double minX = Double.POSITIVE_INFINITY;
                double minY = Double.POSITIVE_INFINITY;
                double minZ = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;
                double maxZ = Double.NEGATIVE_INFINITY;
                float area = 0.0F;
                long identity = 0xcbf29ce484222325L;
                for (ThermalFace face : members) {
                    Vec3 center = faceCenter(face);
                    x += center.x * face.area();
                    y += center.y * face.area();
                    z += center.z * face.area();
                    double halfX = face.direction().getAxis() == Direction.Axis.X ? 0.0D : 0.5D;
                    double halfY = face.direction().getAxis() == Direction.Axis.Y ? 0.0D : 0.5D;
                    double halfZ = face.direction().getAxis() == Direction.Axis.Z ? 0.0D : 0.5D;
                    minX = Math.min(minX, center.x - halfX);
                    minY = Math.min(minY, center.y - halfY);
                    minZ = Math.min(minZ, center.z - halfZ);
                    maxX = Math.max(maxX, center.x + halfX);
                    maxY = Math.max(maxY, center.y + halfY);
                    maxZ = Math.max(maxZ, center.z + halfZ);
                    area += face.area();
                    identity ^= face.source().asLong();
                    identity *= 0x100000001b3L;
                    identity ^= face.direction().ordinal();
                    identity *= 0x100000001b3L;
                }
                Vec3 center = new Vec3(x / area, y / area, z / area);
                RadiantPatch patch = new RadiantPatch(center, first.direction(), first.radiationTemperature(),
                        first.radiationDecayPerBlock(), area, identity,
                        new AABB(minX, minY, minZ, maxX, maxY, maxZ));
                long indexSection = SectionPos.asLong(
                        SectionPos.blockToSectionCoord((int) Math.floor(center.x)),
                        SectionPos.blockToSectionCoord((int) Math.floor(center.y)),
                        SectionPos.blockToSectionCoord((int) Math.floor(center.z)));
                radiantPatchesBySection.computeIfAbsent(indexSection, ignored -> new ArrayList<>()).add(patch);
                maximumLoadedRadiationRange = Math.max(maximumLoadedRadiationRange,
                        Math.abs(first.radiationTemperature()) / first.radiationDecayPerBlock());
            });
        }
        for (Map.Entry<Long, List<RadiantPatch>> entry : radiantPatchesBySection.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        radiantPatchesDirty = false;
    }

    private MergeKey mergeKey(ThermalFace face) {
        return new MergeKey(face.direction(), facePlane(face),
                face.radiationTemperature() == null ? 0 : Float.floatToIntBits(face.radiationTemperature()),
                face.radiationDecayPerBlock() == null ? 0 : Float.floatToIntBits(face.radiationDecayPerBlock()));
    }

    private int facePlane(ThermalFace face) {
        return switch (face.direction().getAxis()) {
            case X -> face.source().getX();
            case Y -> face.source().getY();
            case Z -> face.source().getZ();
        };
    }

    private int firstPatchCoordinate(ThermalFace face) {
        return switch (face.direction().getAxis()) {
            case X -> face.source().getY();
            case Y, Z -> face.source().getX();
        };
    }

    private int secondPatchCoordinate(ThermalFace face) {
        return switch (face.direction().getAxis()) {
            case X, Y -> face.source().getZ();
            case Z -> face.source().getY();
        };
    }

    private static long patchCoordinateKey(int first, int second) {
        return ((long) first << 32) ^ (second & 0xffffffffL);
    }

    private static Vec3 faceCenter(ThermalFace face) {
        return Vec3.atCenterOf(face.source()).add(face.direction().getStepX() * 0.501D,
                face.direction().getStepY() * 0.501D, face.direction().getStepZ() * 0.501D);
    }

    private record MergeKey(Direction direction, int plane, int radiationTemperatureBits, int decayBits) {
    }

    private static final class PatchMergeGroup implements GreedyPatchMerger.CellStore<ThermalFace> {
        private final Long2ObjectOpenHashMap<ThermalFace> remainingFaces = new Long2ObjectOpenHashMap<>();
        private final List<ThermalFace> orderedFaces = new ArrayList<>();

        private void add(ThermalFace face, int firstCoordinate, int secondCoordinate) {
            remainingFaces.put(patchCoordinateKey(firstCoordinate, secondCoordinate), face);
            orderedFaces.add(face);
        }

        @Override
        public boolean contains(int firstCoordinate, int secondCoordinate) {
            return remainingFaces.containsKey(patchCoordinateKey(firstCoordinate, secondCoordinate));
        }

        @Override
        public ThermalFace remove(int firstCoordinate, int secondCoordinate) {
            return remainingFaces.remove(patchCoordinateKey(firstCoordinate, secondCoordinate));
        }

        @Override
        public boolean isEmpty() {
            return remainingFaces.isEmpty();
        }
    }
}
