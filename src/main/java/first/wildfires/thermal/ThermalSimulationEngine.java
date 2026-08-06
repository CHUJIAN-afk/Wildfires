package first.wildfires.thermal;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pure numeric Section solver. Every input is an immutable snapshot assembled on the server thread;
 * the implementation intentionally has no Minecraft or Forge dependency and may run entirely off-thread.
 */
final class ThermalSimulationEngine {

    static final int SIZE = 16;
    static final int VOLUME = SIZE * SIZE * SIZE;
    static final int WEST = 0;
    static final int EAST = 1;
    static final int DOWN = 2;
    static final int UP = 3;
    static final int NORTH = 4;
    static final int SOUTH = 5;
    static final int DIRECTION_COUNT = 6;
    static final int CROSS_SECTION_FLAG = VOLUME;
    static final int LOCAL_INDEX_MASK = VOLUME - 1;

    private static final float STANDARD_STEP_SECONDS = 0.125F;
    private static final float AIR_HEAT_CAPACITY = 1.0F;
    private static final float MAX_PASSIVE_COEFFICIENT = 0.95F;
    private static final float MAX_ABSOLUTE_TEMPERATURE = 3276.7F;
    private static final float WAKE_THRESHOLD = 0.01F;
    private static final float STABLE_THRESHOLD = 0.005F;
    private static final int[][] NEIGHBOR_CODES = createNeighborCodes();

    private ThermalSimulationEngine() {
    }

    static BatchResult solve(BatchInput batch) {
        checkCancelled(batch.cancellationToken());
        Map<Long, MutableSectionState> states = new HashMap<>();
        for (SectionInput input : batch.sections().values()) {
            states.put(input.sectionKey(), new MutableSectionState(input));
        }
        int maximumSubsteps = 0;
        for (JobInput job : batch.jobs()) {
            maximumSubsteps = Math.max(maximumSubsteps, job.substeps());
            MutableSectionState state = states.get(job.sectionKey());
            if (state != null) {
                state.selected = true;
                state.forceDue = false;
            }
        }

        Set<CellRef> externalActivations = new HashSet<>();
        int processedCells = 0;
        for (int substep = 0; substep < maximumSubsteps; substep++) {
            checkCancelled(batch.cancellationToken());
            for (MutableSectionState state : states.values()) {
                state.updateMask = null;
            }
            List<JobInput> activeJobs = new ArrayList<>();
            for (JobInput job : batch.jobs()) {
                if (substep >= job.substeps()) {
                    continue;
                }
                MutableSectionState state = states.get(job.sectionKey());
                if (state == null) {
                    continue;
                }
                BitSet updateSet = expandedActiveCells(state.active);
                updateSet.and(state.input.preparedCells());
                state.updateMask = updateSet;
                activeJobs.add(job);
            }

            List<ForkJoinTask<StepResult>> tasks = new ArrayList<>(activeJobs.size());
            for (JobInput job : activeJobs) {
                tasks.add(ForkJoinTask.adapt(() -> computeSection(batch.settings(), states,
                        states.get(job.sectionKey()), job.stepSeconds(),
                        batch.cancellationToken())));
            }
            ForkJoinTask.invokeAll(tasks);

            List<StepResult> results = new ArrayList<>(tasks.size());
            for (ForkJoinTask<StepResult> task : tasks) {
                StepResult result = task.join();
                results.add(result);
                processedCells += result.updateSet().cardinality();
            }

            Set<CellRef> substepActivations = new HashSet<>();
            for (StepResult result : results) {
                MutableSectionState state = states.get(result.sectionKey());
                state.visible = result.visible();
                state.hidden = result.hidden();
                state.storageChanged |= result.maximumStorageChange() > 0.0F;
                if (result.maximumSolverChange() < STABLE_THRESHOLD) {
                    state.stableCycles++;
                    if (state.stableCycles < 4) {
                        result.nextActive().or(result.updateSet());
                    } else {
                        result.nextActive().clear();
                    }
                } else {
                    state.stableCycles = 0;
                }
                state.active = result.nextActive();
                substepActivations.addAll(result.cellsToActivate());
            }

            for (CellRef activation : substepActivations) {
                MutableSectionState target = states.get(activation.sectionKey());
                if (target == null || !target.input.airCells().get(activation.localIndex())) {
                    continue;
                }
                if (target.selected) {
                    target.active.set(activation.localIndex());
                    target.stableCycles = 0;
                    if (target.updateMask == null) {
                        target.forceDue = true;
                    }
                } else {
                    externalActivations.add(activation);
                }
            }
        }

        Map<Long, SectionResult> sectionResults = new HashMap<>();
        for (JobInput job : batch.jobs()) {
            MutableSectionState state = states.get(job.sectionKey());
            if (state == null) {
                continue;
            }
            int hiddenCount = 0;
            for (float temperature : state.hidden) {
                if (temperature != 0.0F) {
                    hiddenCount++;
                }
            }
            sectionResults.put(job.sectionKey(), new SectionResult(job.sectionKey(), state.visible,
                    hiddenCount == 0 ? null : state.hidden, hiddenCount, state.active,
                    state.stableCycles, state.forceDue, state.storageChanged));
        }
        return new BatchResult(batch.revision(), batch.simulationTick(), Map.copyOf(sectionResults),
                Set.copyOf(externalActivations), processedCells);
    }

    private static StepResult computeSection(Settings settings, Map<Long, MutableSectionState> states,
                                             MutableSectionState section, float dtSeconds,
                                             CancellationToken cancellationToken) {
        checkCancelled(cancellationToken);
        BitSet updateSet = section.updateMask == null ? new BitSet(VOLUME) : section.updateMask;
        float[] pendingVisible = section.visible.clone();
        float[] pendingHidden = section.hidden.clone();
        BitSet nextActive = new BitSet(VOLUME);
        Set<CellRef> cellsToActivate = new HashSet<>();
        float maximumStorageChange = 0.0F;
        float maximumSolverChange = 0.0F;

        float diffusion = normalizedCoefficient(settings.laplacianCoefficient(), dtSeconds);
        float hotBuoyancy = normalizedConservativePairCoefficient(settings.buoyancyCoefficient(), dtSeconds);
        float coldSinking = normalizedConservativePairCoefficient(settings.coldSinkingCoefficient(), dtSeconds);
        if (!settings.laplacianEnabled()) {
            diffusion = 0.0F;
        }
        float maximumCombinedTransfer = diffusion + 2.0F * Math.max(hotBuoyancy, coldSinking);
        if (maximumCombinedTransfer > MAX_PASSIVE_COEFFICIENT) {
            float scale = MAX_PASSIVE_COEFFICIENT / maximumCombinedTransfer;
            diffusion *= scale;
            hotBuoyancy *= scale;
            coldSinking *= scale;
        }

        for (int index = updateSet.nextSetBit(0); index >= 0; index = updateSet.nextSetBit(index + 1)) {
            if ((index & 255) == 0) {
                checkCancelled(cancellationToken);
            }
            float oldVisibleTemperature = section.visible[index];
            float oldHiddenTemperature = section.hidden[index];
            float oldSolverTemperature = oldVisibleTemperature != 0.0F
                    ? oldVisibleTemperature : oldHiddenTemperature;
            if (!section.input.airCells().get(index)) {
                pendingVisible[index] = 0.0F;
                pendingHidden[index] = 0.0F;
                maximumStorageChange = Math.max(maximumStorageChange,
                        Math.max(Math.abs(oldVisibleTemperature), Math.abs(oldHiddenTemperature)));
                maximumSolverChange = Math.max(maximumSolverChange, Math.abs(oldSolverTemperature));
                if (oldSolverTemperature != 0.0F) {
                    queueAirNeighbors(states, section, index, cellsToActivate);
                }
                continue;
            }

            float passiveDelta = 0.0F;
            if (diffusion > 0.0F) {
                float neighborDifference = 0.0F;
                for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                    int neighborCode = neighborCode(index, direction);
                    int neighborIndex = neighborCode & LOCAL_INDEX_MASK;
                    MutableSectionState neighbor = (neighborCode & CROSS_SECTION_FLAG) == 0
                            ? section : states.get(section.input.neighborSections()[direction]);
                    if (!isAir(neighbor, neighborIndex)) {
                        continue;
                    }
                    float neighborTemperature = solverTemperature(neighbor, neighborIndex);
                    neighborDifference += neighborTemperature - oldSolverTemperature;
                    if (!isCellScheduled(neighbor, neighborIndex)
                            && Math.abs(neighborTemperature - oldSolverTemperature) >= WAKE_THRESHOLD) {
                        cellsToActivate.add(new CellRef(neighbor.input.sectionKey(), neighborIndex));
                    }
                }
                passiveDelta += diffusion * neighborDifference / DIRECTION_COUNT;
            }

            passiveDelta += buoyancyDelta(states, index, oldSolverTemperature,
                    hotBuoyancy, coldSinking, section, cellsToActivate);
            float passiveTemperature = clampTemperature(oldSolverTemperature + passiveDelta);
            float boundaryTemperature = clampTemperature(passiveTemperature
                    + boundaryLoss(section.input.boundaries()[index], passiveTemperature, dtSeconds));
            float candidate = clampTemperature(boundaryTemperature
                    + sourceExchange(section.input.heatSources()[index], boundaryTemperature, dtSeconds));
            float newVisibleTemperature = 0.0F;
            float newHiddenTemperature = 0.0F;
            if (Math.abs(candidate) >= settings.hiddenTemperatureCutoff()) {
                if (settings.airTemperatureCutoff() <= 0.0F
                        || Math.abs(candidate) >= settings.airTemperatureCutoff()) {
                    newVisibleTemperature = candidate;
                } else {
                    newHiddenTemperature = candidate;
                }
            }

            pendingVisible[index] = newVisibleTemperature;
            pendingHidden[index] = newHiddenTemperature;
            float newSolverTemperature = newVisibleTemperature != 0.0F
                    ? newVisibleTemperature : newHiddenTemperature;
            float visibleChange = Math.abs(newVisibleTemperature - oldVisibleTemperature);
            float hiddenChange = Math.abs(newHiddenTemperature - oldHiddenTemperature);
            float solverChange = Math.abs(newSolverTemperature - oldSolverTemperature);
            maximumStorageChange = Math.max(maximumStorageChange, Math.max(visibleChange, hiddenChange));
            maximumSolverChange = Math.max(maximumSolverChange, solverChange);
            if (newSolverTemperature != 0.0F && solverChange >= STABLE_THRESHOLD) {
                nextActive.set(index);
            }
            if ((oldSolverTemperature == 0.0F) != (newSolverTemperature == 0.0F)) {
                queueAirNeighbors(states, section, index, cellsToActivate);
            }
        }
        return new StepResult(section.input.sectionKey(), updateSet, pendingVisible, pendingHidden,
                nextActive, cellsToActivate, maximumStorageChange, maximumSolverChange);
    }

    private static float buoyancyDelta(Map<Long, MutableSectionState> states, int index,
                                       float temperature, float hotCoefficient,
                                       float coldCoefficient, MutableSectionState current,
                                       Set<CellRef> cellsToActivate) {
        if (hotCoefficient <= 0.0F && coldCoefficient <= 0.0F) {
            return 0.0F;
        }
        float aboveTemperature = scheduledAirTemperature(states, current, index, UP, temperature,
                cellsToActivate);
        float belowTemperature = scheduledAirTemperature(states, current, index, DOWN, temperature,
                cellsToActivate);
        float delta = 0.0F;
        if (temperature > aboveTemperature && (temperature > 0.0F || aboveTemperature < 0.0F)) {
            float coefficient = buoyancyPairCoefficient(temperature, aboveTemperature,
                    hotCoefficient, coldCoefficient);
            delta -= (temperature - aboveTemperature) * coefficient;
        }
        if (belowTemperature > temperature && (belowTemperature > 0.0F || temperature < 0.0F)) {
            float coefficient = buoyancyPairCoefficient(belowTemperature, temperature,
                    hotCoefficient, coldCoefficient);
            delta += (belowTemperature - temperature) * coefficient;
        }
        return delta;
    }

    private static float scheduledAirTemperature(Map<Long, MutableSectionState> states,
                                                 MutableSectionState current, int index, int direction,
                                                 float fallback,
                                                 Set<CellRef> cellsToActivate) {
        int neighborCode = neighborCode(index, direction);
        int neighborIndex = neighborCode & LOCAL_INDEX_MASK;
        MutableSectionState neighbor = (neighborCode & CROSS_SECTION_FLAG) == 0
                ? current : states.get(current.input.neighborSections()[direction]);
        if (!isAir(neighbor, neighborIndex)) {
            return fallback;
        }
        float temperature = solverTemperature(neighbor, neighborIndex);
        if (!isCellScheduled(neighbor, neighborIndex)
                && Math.abs(temperature - fallback) >= WAKE_THRESHOLD) {
            cellsToActivate.add(new CellRef(neighbor.input.sectionKey(), neighborIndex));
        }
        return temperature;
    }

    private static float sourceExchange(HeatSource[] sources, float airTemperature, float dtSeconds) {
        if (sources == null || sources.length == 0) {
            return 0.0F;
        }
        float delta = 0.0F;
        float positiveLimit = 0.0F;
        float negativeLimit = 0.0F;
        for (HeatSource source : sources) {
            float maximumChange = source.faceHeatingRate() * dtSeconds * source.area() / AIR_HEAT_CAPACITY;
            float difference = source.surfaceTemperature() - airTemperature;
            delta += Math.max(-maximumChange, Math.min(maximumChange, difference));
            positiveLimit = Math.max(positiveLimit, difference);
            negativeLimit = Math.min(negativeLimit, difference);
        }
        return Math.max(negativeLimit, Math.min(positiveLimit, delta));
    }

    private static float boundaryLoss(BoundaryTerm[] boundaries, float temperature, float dtSeconds) {
        if (boundaries == null || boundaries.length == 0) {
            return 0.0F;
        }
        float change = 0.0F;
        for (BoundaryTerm boundary : boundaries) {
            float loss = normalizedCoefficient(boundary.loss(), dtSeconds);
            change += -temperature * loss * boundary.area();
        }
        float maximum = Math.abs(temperature);
        return Math.max(-maximum, Math.min(maximum, change));
    }

    private static float buoyancyPairCoefficient(float lowerTemperature, float upperTemperature,
                                                  float hotCoefficient, float coldCoefficient) {
        float difference = lowerTemperature - upperTemperature;
        if (difference <= 0.0F) {
            return 0.0F;
        }
        float hotDifference = Math.max(lowerTemperature, 0.0F) - Math.max(upperTemperature, 0.0F);
        float coldDifference = Math.max(-upperTemperature, 0.0F) - Math.max(-lowerTemperature, 0.0F);
        return (hotDifference * hotCoefficient + coldDifference * coldCoefficient) / difference;
    }

    private static void queueAirNeighbors(Map<Long, MutableSectionState> states,
                                          MutableSectionState current, int index,
                                          Set<CellRef> cellsToActivate) {
        for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
            int neighborCode = neighborCode(index, direction);
            int neighborIndex = neighborCode & LOCAL_INDEX_MASK;
            MutableSectionState neighbor = (neighborCode & CROSS_SECTION_FLAG) == 0
                    ? current : states.get(current.input.neighborSections()[direction]);
            if (isAir(neighbor, neighborIndex)) {
                cellsToActivate.add(new CellRef(neighbor.input.sectionKey(), neighborIndex));
            }
        }
    }

    private static boolean isCellScheduled(MutableSectionState state, int index) {
        return state.updateMask != null && state.updateMask.get(index);
    }

    private static boolean isAir(MutableSectionState state, int index) {
        return state != null && state.input.airCells().get(index);
    }

    private static float solverTemperature(MutableSectionState state, int index) {
        float visible = state.visible[index];
        return visible != 0.0F ? visible : state.hidden[index];
    }

    static int neighborCode(int index, int direction) {
        return NEIGHBOR_CODES[direction][index];
    }

    private static int[][] createNeighborCodes() {
        int[][] codes = new int[DIRECTION_COUNT][VOLUME];
        for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
            for (int index = 0; index < VOLUME; index++) {
                codes[direction][index] = computeNeighborCode(index, direction);
            }
        }
        return codes;
    }

    private static int computeNeighborCode(int index, int direction) {
        int x = index & 15;
        int z = (index >>> 4) & 15;
        int y = (index >>> 8) & 15;
        return switch (direction) {
            case WEST -> x > 0 ? index - 1 : CROSS_SECTION_FLAG | index + 15;
            case EAST -> x < 15 ? index + 1 : CROSS_SECTION_FLAG | index - 15;
            case DOWN -> y > 0 ? index - 256 : CROSS_SECTION_FLAG | index + 15 * 256;
            case UP -> y < 15 ? index + 256 : CROSS_SECTION_FLAG | index - 15 * 256;
            case NORTH -> z > 0 ? index - 16 : CROSS_SECTION_FLAG | index + 15 * 16;
            case SOUTH -> z < 15 ? index + 16 : CROSS_SECTION_FLAG | index - 15 * 16;
            default -> throw new IllegalArgumentException("Unknown direction " + direction);
        };
    }

    static BitSet expandedActiveCells(BitSet active) {
        BitSet cells = (BitSet) active.clone();
        for (int index = active.nextSetBit(0); index >= 0; index = active.nextSetBit(index + 1)) {
            for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                int code = neighborCode(index, direction);
                if ((code & CROSS_SECTION_FLAG) == 0) {
                    cells.set(code);
                }
            }
        }
        return cells;
    }

    /** Expands one immutable source mask into its local and already-selected neighbor Section masks. */
    static void expandPreparedMask(BitSet source, BitSet localExpanded, BitSet[] neighborExpanded) {
        if (neighborExpanded.length != DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid neighbor mask count");
        }
        for (int index = source.nextSetBit(0); index >= 0; index = source.nextSetBit(index + 1)) {
            for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                int code = neighborCode(index, direction);
                int neighborIndex = code & LOCAL_INDEX_MASK;
                if ((code & CROSS_SECTION_FLAG) == 0) {
                    localExpanded.set(neighborIndex);
                } else if (neighborExpanded[direction] != null) {
                    neighborExpanded[direction].set(neighborIndex);
                }
            }
        }
    }

    private static float normalizedCoefficient(float baseCoefficient, float dtSeconds) {
        if (baseCoefficient <= 0.0F) return 0.0F;
        if (baseCoefficient >= 1.0F) return 1.0F;
        return 1.0F - (float) Math.pow(1.0F - baseCoefficient, dtSeconds / STANDARD_STEP_SECONDS);
    }

    private static float normalizedConservativePairCoefficient(float baseCoefficient, float dtSeconds) {
        if (baseCoefficient <= 0.0F) return 0.0F;
        float bounded = Math.min(0.5F, baseCoefficient);
        if (bounded >= 0.5F) return 0.5F;
        return 0.5F * (1.0F - (float) Math.pow(1.0F - 2.0F * bounded,
                dtSeconds / STANDARD_STEP_SECONDS));
    }

    private static float clampTemperature(float value) {
        return Math.max(-MAX_ABSOLUTE_TEMPERATURE, Math.min(MAX_ABSOLUTE_TEMPERATURE, value));
    }

    private static void checkCancelled(CancellationToken cancellationToken) {
        if (cancellationToken.cancelled() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Thermal simulation cancelled");
        }
    }

    record Settings(boolean laplacianEnabled, float laplacianCoefficient, float buoyancyCoefficient,
                    float coldSinkingCoefficient, float airTemperatureCutoff,
                    float hiddenTemperatureCutoff) {
    }

    record BatchInput(long revision, long simulationTick, Settings settings, List<JobInput> jobs,
                      Map<Long, SectionInput> sections, CancellationToken cancellationToken) {
        BatchInput {
            jobs = List.copyOf(jobs);
            sections = Map.copyOf(sections);
        }
    }

    static final class CancellationToken {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        void cancel() {
            cancelled.set(true);
        }

        boolean cancelled() {
            return cancelled.get();
        }
    }

    record JobInput(long sectionKey, int substeps, float stepSeconds) {
    }

    static final class SectionInput {
        private final long sectionKey;
        private final float[] visible;
        private final float[] hidden;
        private final BitSet airCells;
        private final BitSet preparedCells;
        private final BitSet active;
        private final int stableCycles;
        private final boolean forceDue;
        private final long[] neighborSections;
        private final BoundaryTerm[][] boundaries;
        private final HeatSource[][] heatSources;

        SectionInput(long sectionKey, float[] visible, float[] hidden, BitSet airCells,
                     BitSet preparedCells, BitSet active, int stableCycles, boolean forceDue,
                     long[] neighborSections, BoundaryTerm[][] boundaries,
                     HeatSource[][] heatSources) {
            this(sectionKey, visible, hidden, airCells, preparedCells, active, stableCycles, forceDue,
                    neighborSections, boundaries, heatSources, false);
        }

        static SectionInput owned(long sectionKey, float[] visible, float[] hidden, BitSet airCells,
                                  BitSet preparedCells, BitSet active, int stableCycles, boolean forceDue,
                                  long[] neighborSections, BoundaryTerm[][] boundaries,
                                  HeatSource[][] heatSources) {
            return new SectionInput(sectionKey, visible, hidden, airCells, preparedCells, active,
                    stableCycles, forceDue, neighborSections, boundaries, heatSources, true);
        }

        private SectionInput(long sectionKey, float[] visible, float[] hidden, BitSet airCells,
                             BitSet preparedCells, BitSet active, int stableCycles, boolean forceDue,
                             long[] neighborSections, BoundaryTerm[][] boundaries,
                             HeatSource[][] heatSources, boolean takeOwnership) {
            if (visible.length != VOLUME || hidden.length != VOLUME
                    || neighborSections.length != DIRECTION_COUNT
                    || boundaries.length != VOLUME || heatSources.length != VOLUME) {
                throw new IllegalArgumentException("Invalid thermal simulation snapshot dimensions");
            }
            this.sectionKey = sectionKey;
            this.visible = takeOwnership ? visible : visible.clone();
            this.hidden = takeOwnership ? hidden : hidden.clone();
            this.airCells = takeOwnership ? airCells : (BitSet) airCells.clone();
            this.preparedCells = takeOwnership ? preparedCells : (BitSet) preparedCells.clone();
            this.active = takeOwnership ? active : (BitSet) active.clone();
            this.stableCycles = stableCycles;
            this.forceDue = forceDue;
            this.neighborSections = takeOwnership ? neighborSections : neighborSections.clone();
            this.boundaries = takeOwnership ? boundaries : cloneTerms(boundaries);
            this.heatSources = takeOwnership ? heatSources : cloneSources(heatSources);
        }

        long sectionKey() { return sectionKey; }
        float[] visible() { return visible; }
        float[] hidden() { return hidden; }
        BitSet airCells() { return airCells; }
        BitSet preparedCells() { return preparedCells; }
        BitSet active() { return active; }
        int stableCycles() { return stableCycles; }
        boolean forceDue() { return forceDue; }
        long[] neighborSections() { return neighborSections; }
        BoundaryTerm[][] boundaries() { return boundaries; }
        HeatSource[][] heatSources() { return heatSources; }

        private static BoundaryTerm[][] cloneTerms(BoundaryTerm[][] source) {
            BoundaryTerm[][] copy = source.clone();
            for (int index = 0; index < copy.length; index++) {
                if (copy[index] != null) {
                    copy[index] = copy[index].clone();
                }
            }
            return copy;
        }

        private static HeatSource[][] cloneSources(HeatSource[][] source) {
            HeatSource[][] copy = source.clone();
            for (int index = 0; index < copy.length; index++) {
                if (copy[index] != null) {
                    copy[index] = copy[index].clone();
                }
            }
            return copy;
        }
    }

    record BoundaryTerm(float loss, float area) {
    }

    record HeatSource(float surfaceTemperature, float faceHeatingRate, float area) {
    }

    record CellRef(long sectionKey, int localIndex) {
    }

    record SectionResult(long sectionKey, float[] visible, float[] hidden, int hiddenCount,
                         BitSet active, int stableCycles, boolean forceDue, boolean storageChanged) {
    }

    record BatchResult(long revision, long simulationTick, Map<Long, SectionResult> sections,
                       Set<CellRef> externalActivations, int processedCells) {
    }

    private record StepResult(long sectionKey, BitSet updateSet, float[] visible, float[] hidden,
                              BitSet nextActive, Set<CellRef> cellsToActivate,
                              float maximumStorageChange, float maximumSolverChange) {
    }

    private static final class MutableSectionState {
        private final SectionInput input;
        private float[] visible;
        private float[] hidden;
        private BitSet active;
        private int stableCycles;
        private boolean forceDue;
        private boolean storageChanged;
        private boolean selected;
        private BitSet updateMask;

        private MutableSectionState(SectionInput input) {
            this.input = input;
            this.visible = input.visible();
            this.hidden = input.hidden();
            this.active = (BitSet) input.active().clone();
            this.stableCycles = input.stableCycles();
            this.forceDue = input.forceDue();
        }
    }
}
