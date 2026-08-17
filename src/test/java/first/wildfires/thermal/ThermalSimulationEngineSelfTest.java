package first.wildfires.thermal;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CancellationException;

/** Plain-Java regression checks runnable without a game bootstrap or a unit-test framework. */
public final class ThermalSimulationEngineSelfTest {

    private static final float EPSILON = 0.0001F;

    private ThermalSimulationEngineSelfTest() {
    }

    public static void main(String[] args) {
        sourceFacesSaturateAtTarget();
        laplacianConservesScheduledPair();
        hotAndColdVerticalTransfersAreConservative();
        boundaryLossUsesSnapshotTerms();
        hiddenLayerKeepsSubVisibleTemperature();
        stableSectionSleepsAfterFourSubsteps();
        neighborCodesMatchSectionCoordinates();
        preparedMaskExpansionMatchesNaiveNeighbors();
        sectionInputDefensivelyCopiesCallerData();
        crossSectionBarrierIsDeterministic();
        cancelledBatchCannotProduceAResult();
        enclosedSourceRadiationUsesConfiguredTemperature();
        GreedyPatchMergerSelfTest.runAll();
        System.out.println("ThermalSimulationEngineSelfTest: all checks passed");
    }

    private static void sourceFacesSaturateAtTarget() {
        int cell = index(8, 8, 8);
        ThermalSimulationEngine.HeatSource[] sources = new ThermalSimulationEngine.HeatSource[4];
        for (int i = 0; i < sources.length; i++) {
            sources[i] = new ThermalSimulationEngine.HeatSource(20.0F, 100.0F, 1.0F);
        }
        ThermalSimulationEngine.SectionInput section = section(1L, temperatures(), cells(cell), cells(cell),
                null, heat(cell, sources), neighbors());
        ThermalSimulationEngine.BatchResult result = solve(settings(false, 0.0F, 0.0F, 0.0F, 1.0F, 0.05F),
                List.of(new ThermalSimulationEngine.JobInput(1L, 1, 0.125F)), Map.of(1L, section));
        assertClose(20.0F, result.sections().get(1L).visible()[cell],
                "four faces must not stack a 20 WTU target to 80 WTU");
    }

    private static void laplacianConservesScheduledPair() {
        int hot = index(7, 8, 8);
        int cold = index(8, 8, 8);
        float[] visible = new float[ThermalSimulationEngine.VOLUME];
        visible[hot] = 10.0F;
        ThermalSimulationEngine.SectionInput section = section(1L, visible, cells(hot, cold),
                cells(hot, cold), null, null, neighbors());
        ThermalSimulationEngine.BatchResult result = solve(settings(true, 0.6F, 0.0F, 0.0F, 0.0F, 0.01F),
                List.of(new ThermalSimulationEngine.JobInput(1L, 1, 0.125F)), Map.of(1L, section));
        float[] output = result.sections().get(1L).visible();
        assertClose(10.0F, output[hot] + output[cold], "scheduled Laplacian pair must conserve heat");
        assertClose(9.0F, output[hot], "hot cell diffusion result");
        assertClose(1.0F, output[cold], "cold cell diffusion result");
    }

    private static void hotAndColdVerticalTransfersAreConservative() {
        int lower = index(8, 8, 8);
        int upper = index(8, 9, 8);

        float[] hotVisible = new float[ThermalSimulationEngine.VOLUME];
        hotVisible[lower] = 10.0F;
        ThermalSimulationEngine.SectionInput hotSection = section(1L, hotVisible, cells(lower, upper),
                cells(lower, upper), null, null, neighbors());
        float[] hotResult = solve(settings(false, 0.0F, 0.05F, 0.05F, 0.0F, 0.01F),
                List.of(new ThermalSimulationEngine.JobInput(1L, 1, 0.125F)), Map.of(1L, hotSection))
                .sections().get(1L).visible();
        assertClose(10.0F, hotResult[lower] + hotResult[upper], "hot rise must conserve the pair");
        assertClose(9.5F, hotResult[lower], "hot lower-cell transfer");
        assertClose(0.5F, hotResult[upper], "hot upper-cell transfer");

        float[] coldVisible = new float[ThermalSimulationEngine.VOLUME];
        coldVisible[upper] = -10.0F;
        ThermalSimulationEngine.SectionInput coldSection = section(1L, coldVisible, cells(lower, upper),
                cells(lower, upper), null, null, neighbors());
        float[] coldResult = solve(settings(false, 0.0F, 0.05F, 0.05F, 0.0F, 0.01F),
                List.of(new ThermalSimulationEngine.JobInput(1L, 1, 0.125F)), Map.of(1L, coldSection))
                .sections().get(1L).visible();
        assertClose(-10.0F, coldResult[lower] + coldResult[upper], "cold sinking must conserve the pair");
        assertClose(-0.5F, coldResult[lower], "cold lower-cell transfer");
        assertClose(-9.5F, coldResult[upper], "cold upper-cell transfer");
    }

    private static void hiddenLayerKeepsSubVisibleTemperature() {
        int cell = index(8, 8, 8);
        ThermalSimulationEngine.HeatSource source = new ThermalSimulationEngine.HeatSource(0.5F, 100.0F, 1.0F);
        ThermalSimulationEngine.SectionInput section = section(1L, temperatures(), cells(cell), cells(cell),
                null, heat(cell, new ThermalSimulationEngine.HeatSource[]{source}), neighbors());
        ThermalSimulationEngine.SectionResult result = solve(settings(false, 0.0F, 0.0F, 0.0F, 1.0F, 0.05F),
                List.of(new ThermalSimulationEngine.JobInput(1L, 1, 0.125F)), Map.of(1L, section))
                .sections().get(1L);
        assertClose(0.0F, result.visible()[cell], "sub-visible temperature must not enter gameplay field");
        assertClose(0.5F, result.hidden()[cell], "sub-visible temperature must remain in hidden solver field");
    }

    private static void boundaryLossUsesSnapshotTerms() {
        int cell = index(8, 8, 8);
        float[] visible = new float[ThermalSimulationEngine.VOLUME];
        visible[cell] = 10.0F;
        ThermalSimulationEngine.BoundaryTerm[][] boundaries =
                new ThermalSimulationEngine.BoundaryTerm[ThermalSimulationEngine.VOLUME][];
        boundaries[cell] = new ThermalSimulationEngine.BoundaryTerm[]{
                new ThermalSimulationEngine.BoundaryTerm(0.1F, 1.0F)
        };
        ThermalSimulationEngine.SectionInput section = section(1L, visible, cells(cell), cells(cell),
                boundaries, null, neighbors());
        float result = solve(settings(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.01F),
                List.of(new ThermalSimulationEngine.JobInput(1L, 1, 0.125F)), Map.of(1L, section))
                .sections().get(1L).visible()[cell];
        assertClose(9.0F, result, "wall loss must use the immutable boundary snapshot");
    }

    private static void stableSectionSleepsAfterFourSubsteps() {
        int cell = index(8, 8, 8);
        float[] visible = new float[ThermalSimulationEngine.VOLUME];
        visible[cell] = 10.0F;
        ThermalSimulationEngine.SectionInput section = section(1L, visible, cells(cell), cells(cell),
                null, null, neighbors());
        ThermalSimulationEngine.SectionResult result = solve(settings(false, 0.0F, 0.0F, 0.0F, 0.0F, 0.01F),
                List.of(new ThermalSimulationEngine.JobInput(1L, 4, 0.125F)), Map.of(1L, section))
                .sections().get(1L);
        if (!result.active().isEmpty() || result.stableCycles() != 4) {
            throw new AssertionError("stable Section did not sleep after four unchanged substeps");
        }
    }

    private static void neighborCodesMatchSectionCoordinates() {
        int[] opposite = {
                ThermalSimulationEngine.EAST, ThermalSimulationEngine.WEST,
                ThermalSimulationEngine.UP, ThermalSimulationEngine.DOWN,
                ThermalSimulationEngine.SOUTH, ThermalSimulationEngine.NORTH
        };
        for (int index = 0; index < ThermalSimulationEngine.VOLUME; index++) {
            int x = index & 15;
            int z = (index >>> 4) & 15;
            int y = (index >>> 8) & 15;
            for (int direction = 0; direction < ThermalSimulationEngine.DIRECTION_COUNT; direction++) {
                int code = ThermalSimulationEngine.neighborCode(index, direction);
                int neighborIndex = code & ThermalSimulationEngine.LOCAL_INDEX_MASK;
                boolean crosses = (code & ThermalSimulationEngine.CROSS_SECTION_FLAG) != 0;
                boolean expectedCrossing = switch (direction) {
                    case ThermalSimulationEngine.WEST -> x == 0;
                    case ThermalSimulationEngine.EAST -> x == 15;
                    case ThermalSimulationEngine.DOWN -> y == 0;
                    case ThermalSimulationEngine.UP -> y == 15;
                    case ThermalSimulationEngine.NORTH -> z == 0;
                    case ThermalSimulationEngine.SOUTH -> z == 15;
                    default -> throw new AssertionError("unknown direction");
                };
                if (crosses != expectedCrossing || neighborIndex < 0
                        || neighborIndex >= ThermalSimulationEngine.VOLUME) {
                    throw new AssertionError("invalid encoded neighbor for cell " + index
                            + " direction " + direction);
                }
                int reverse = ThermalSimulationEngine.neighborCode(neighborIndex, opposite[direction]);
                if ((reverse & ThermalSimulationEngine.LOCAL_INDEX_MASK) != index
                        || ((reverse & ThermalSimulationEngine.CROSS_SECTION_FLAG) != 0) != crosses) {
                    throw new AssertionError("neighbor encoding is not reversible for cell " + index
                            + " direction " + direction);
                }
            }
        }
    }

    private static void sectionInputDefensivelyCopiesCallerData() {
        int cell = index(8, 8, 8);
        float[] visible = temperatures();
        float[] hidden = temperatures();
        visible[cell] = 4.0F;
        hidden[cell] = 0.5F;
        BitSet air = cells(cell);
        BitSet prepared = cells(cell);
        BitSet active = cells(cell);
        long[] neighborSections = neighbors();
        ThermalSimulationEngine.BoundaryTerm[][] boundaries =
                new ThermalSimulationEngine.BoundaryTerm[ThermalSimulationEngine.VOLUME][];
        ThermalSimulationEngine.HeatSource[][] sources =
                new ThermalSimulationEngine.HeatSource[ThermalSimulationEngine.VOLUME][];
        boundaries[cell] = new ThermalSimulationEngine.BoundaryTerm[]{
                new ThermalSimulationEngine.BoundaryTerm(0.1F, 1.0F)
        };
        sources[cell] = new ThermalSimulationEngine.HeatSource[]{
                new ThermalSimulationEngine.HeatSource(20.0F, 2.0F, 1.0F)
        };
        ThermalSimulationEngine.SectionInput input = new ThermalSimulationEngine.SectionInput(1L,
                visible, hidden, air, prepared, active, 0, true, neighborSections, boundaries, sources);

        visible[cell] = 99.0F;
        hidden[cell] = 99.0F;
        air.clear();
        prepared.clear();
        active.clear();
        neighborSections[ThermalSimulationEngine.WEST] = 99L;
        boundaries[cell][0] = new ThermalSimulationEngine.BoundaryTerm(0.9F, 1.0F);
        sources[cell][0] = new ThermalSimulationEngine.HeatSource(99.0F, 99.0F, 1.0F);

        assertClose(4.0F, input.visible()[cell], "SectionInput must copy visible temperatures");
        assertClose(0.5F, input.hidden()[cell], "SectionInput must copy hidden temperatures");
        if (!input.airCells().get(cell) || !input.preparedCells().get(cell)
                || !input.active().get(cell)
                || input.neighborSections()[ThermalSimulationEngine.WEST] == 99L
                || input.boundaries()[cell][0].loss() != 0.1F
                || input.heatSources()[cell][0].surfaceTemperature() != 20.0F) {
            throw new AssertionError("SectionInput did not isolate caller-owned snapshot data");
        }
    }

    private static void preparedMaskExpansionMatchesNaiveNeighbors() {
        Random random = new Random(0x57494C4446495245L);
        BitSet source = new BitSet(ThermalSimulationEngine.VOLUME);
        for (int index = 0; index < ThermalSimulationEngine.VOLUME; index++) {
            if (random.nextInt(7) == 0) {
                source.set(index);
            }
        }
        BitSet actualLocal = (BitSet) source.clone();
        BitSet expectedLocal = (BitSet) source.clone();
        BitSet[] actualNeighbors = new BitSet[ThermalSimulationEngine.DIRECTION_COUNT];
        BitSet[] expectedNeighbors = new BitSet[ThermalSimulationEngine.DIRECTION_COUNT];
        for (int direction = 0; direction < ThermalSimulationEngine.DIRECTION_COUNT; direction++) {
            actualNeighbors[direction] = new BitSet(ThermalSimulationEngine.VOLUME);
            expectedNeighbors[direction] = new BitSet(ThermalSimulationEngine.VOLUME);
        }
        ThermalSimulationEngine.expandPreparedMask(source, actualLocal, actualNeighbors);
        for (int index = source.nextSetBit(0); index >= 0; index = source.nextSetBit(index + 1)) {
            for (int direction = 0; direction < ThermalSimulationEngine.DIRECTION_COUNT; direction++) {
                int code = ThermalSimulationEngine.neighborCode(index, direction);
                int neighborIndex = code & ThermalSimulationEngine.LOCAL_INDEX_MASK;
                if ((code & ThermalSimulationEngine.CROSS_SECTION_FLAG) == 0) {
                    expectedLocal.set(neighborIndex);
                } else {
                    expectedNeighbors[direction].set(neighborIndex);
                }
            }
        }
        if (!actualLocal.equals(expectedLocal)) {
            throw new AssertionError("local prepared-mask expansion differs from naive neighbors");
        }
        for (int direction = 0; direction < ThermalSimulationEngine.DIRECTION_COUNT; direction++) {
            if (!actualNeighbors[direction].equals(expectedNeighbors[direction])) {
                throw new AssertionError("cross-Section prepared-mask expansion differs in direction "
                        + direction);
            }
        }
    }

    private static void crossSectionBarrierIsDeterministic() {
        long westKey = 11L;
        long eastKey = 12L;
        int westCell = index(15, 8, 8);
        int eastCell = index(0, 8, 8);
        float[] westVisible = new float[ThermalSimulationEngine.VOLUME];
        westVisible[westCell] = 10.0F;
        long[] westNeighbors = neighbors();
        long[] eastNeighbors = neighbors();
        westNeighbors[ThermalSimulationEngine.EAST] = eastKey;
        eastNeighbors[ThermalSimulationEngine.WEST] = westKey;
        ThermalSimulationEngine.SectionInput west = section(westKey, westVisible, cells(westCell),
                cells(westCell), null, null, westNeighbors);
        ThermalSimulationEngine.SectionInput east = section(eastKey, new float[ThermalSimulationEngine.VOLUME],
                cells(eastCell), cells(eastCell), null, null, eastNeighbors);
        List<ThermalSimulationEngine.JobInput> jobs = List.of(
                new ThermalSimulationEngine.JobInput(westKey, 1, 0.125F),
                new ThermalSimulationEngine.JobInput(eastKey, 1, 0.125F));
        Map<Long, ThermalSimulationEngine.SectionInput> sections = Map.of(westKey, west, eastKey, east);
        ThermalSimulationEngine.BatchResult first = solve(
                settings(true, 0.6F, 0.0F, 0.0F, 0.0F, 0.01F), jobs, sections);
        ThermalSimulationEngine.BatchResult second = solve(
                settings(true, 0.6F, 0.0F, 0.0F, 0.0F, 0.01F), jobs, sections);
        float firstWest = first.sections().get(westKey).visible()[westCell];
        float firstEast = first.sections().get(eastKey).visible()[eastCell];
        assertClose(10.0F, firstWest + firstEast, "cross-Section scheduled pair must conserve heat");
        assertClose(firstWest, second.sections().get(westKey).visible()[westCell],
                "parallel Section result must be deterministic");
        assertClose(firstEast, second.sections().get(eastKey).visible()[eastCell],
                "parallel Section result must be independent of completion order");
    }

    private static void cancelledBatchCannotProduceAResult() {
        ThermalSimulationEngine.CancellationToken token = new ThermalSimulationEngine.CancellationToken();
        token.cancel();
        try {
            ThermalSimulationEngine.solve(new ThermalSimulationEngine.BatchInput(1L, 1L,
                    settings(false, 0.0F, 0.0F, 0.0F, 1.0F, 0.05F), List.of(), Map.of(), token));
            throw new AssertionError("cancelled batch unexpectedly completed");
        } catch (CancellationException expected) {
            // Expected: cancelled epochs must never reach the main-thread commit path.
        }
    }

    private static void enclosedSourceRadiationUsesConfiguredTemperature() {
        ThermalSourceRegistry.ThermalSourceDefinition hotDefinition =
                new ThermalSourceRegistry.ThermalSourceDefinition(0.0F, 0.0F, 100.0F, 50.0F);
        ThermalSourceRegistry.ResolvedThermalSource hot =
                new ThermalSourceRegistry.ResolvedThermalSource(hotDefinition, true, 0.0F, false);
        assertClose(100.0F, ThermalRadiationSolver.selectDirectSourceRadiation(hot),
                "an enclosed hot source must expose its full configured radiation temperature");

        ThermalSourceRegistry.ThermalSourceDefinition coldDefinition =
                new ThermalSourceRegistry.ThermalSourceDefinition(0.0F, 0.0F, -24.0F, 2.0F);
        ThermalSourceRegistry.ResolvedThermalSource cold =
                new ThermalSourceRegistry.ResolvedThermalSource(coldDefinition, true, 0.0F, false);
        assertClose(-24.0F, ThermalRadiationSolver.selectDirectSourceRadiation(cold),
                "an enclosed cold source must preserve the configured negative sign");

        ThermalSourceRegistry.ThermalSourceDefinition dynamicDefinition =
                new ThermalSourceRegistry.ThermalSourceDefinition(100.0F, 0.0F, 40.0F, 2.0F);
        ThermalSourceRegistry.ResolvedThermalSource dynamic =
                new ThermalSourceRegistry.ResolvedThermalSource(dynamicDefinition, true, 50.0F, true);
        assertClose(20.0F, ThermalRadiationSolver.selectDirectSourceRadiation(dynamic),
                "an enclosed dynamic source must preserve resolved runtime radiation scaling");

        ThermalSourceRegistry.ResolvedThermalSource inactive =
                new ThermalSourceRegistry.ResolvedThermalSource(hotDefinition, false, 0.0F, false);
        if (ThermalRadiationSolver.selectDirectSourceRadiation(inactive) != null
                || ThermalRadiationSolver.selectDirectSourceRadiation(null) != null) {
            throw new AssertionError("inactive and unmatched receivers must not use direct radiation");
        }

        ThermalSourceRegistry.ThermalSourceDefinition noRadiationDefinition =
                new ThermalSourceRegistry.ThermalSourceDefinition(30.0F, 2.0F, null, null);
        ThermalSourceRegistry.ResolvedThermalSource noRadiation =
                new ThermalSourceRegistry.ResolvedThermalSource(noRadiationDefinition, true, 30.0F, false);
        if (ThermalRadiationSolver.selectDirectSourceRadiation(noRadiation) != null) {
            throw new AssertionError("enclosed sources without radiation must not use direct radiation");
        }
    }

    private static ThermalSimulationEngine.BatchResult solve(ThermalSimulationEngine.Settings settings,
                                                              List<ThermalSimulationEngine.JobInput> jobs,
                                                              Map<Long, ThermalSimulationEngine.SectionInput> sections) {
        return ThermalSimulationEngine.solve(new ThermalSimulationEngine.BatchInput(1L, 100L, settings,
                jobs, sections, new ThermalSimulationEngine.CancellationToken()));
    }

    private static ThermalSimulationEngine.SectionInput section(long key, float[] visible, BitSet air,
                                                                 BitSet active,
                                                                 ThermalSimulationEngine.BoundaryTerm[][] boundaries,
                                                                 ThermalSimulationEngine.HeatSource[][] heat,
                                                                 long[] neighbors) {
        BitSet prepared = (BitSet) air.clone();
        return new ThermalSimulationEngine.SectionInput(key, visible, new float[ThermalSimulationEngine.VOLUME],
                air, prepared, active, 0, true, neighbors,
                boundaries == null ? new ThermalSimulationEngine.BoundaryTerm[ThermalSimulationEngine.VOLUME][] : boundaries,
                heat == null ? new ThermalSimulationEngine.HeatSource[ThermalSimulationEngine.VOLUME][] : heat);
    }

    private static ThermalSimulationEngine.Settings settings(boolean laplacian, float diffusion,
                                                              float hot, float cold, float visibleCutoff,
                                                              float hiddenCutoff) {
        return new ThermalSimulationEngine.Settings(laplacian, diffusion, hot, cold,
                visibleCutoff, hiddenCutoff);
    }

    private static ThermalSimulationEngine.HeatSource[][] heat(
            int cell, ThermalSimulationEngine.HeatSource[] sources) {
        ThermalSimulationEngine.HeatSource[][] heat =
                new ThermalSimulationEngine.HeatSource[ThermalSimulationEngine.VOLUME][];
        heat[cell] = sources;
        return heat;
    }

    private static float[] temperatures() {
        return new float[ThermalSimulationEngine.VOLUME];
    }

    private static BitSet cells(int first, int... rest) {
        BitSet cells = new BitSet(ThermalSimulationEngine.VOLUME);
        cells.set(first);
        for (int cell : rest) {
            cells.set(cell);
        }
        return cells;
    }

    private static long[] neighbors() {
        return new long[]{-101L, -102L, -103L, -104L, -105L, -106L};
    }

    private static int index(int x, int y, int z) {
        return x | (z << 4) | (y << 8);
    }

    private static void assertClose(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
