package first.wildfires.thermal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Plain-Java equivalence and complexity checks for radiant surface patch layout. */
final class GreedyPatchMergerSelfTest {

    private static final int MAXIMUM_SPAN = 4;

    private GreedyPatchMergerSelfTest() {
    }

    static void runAll() {
        randomizedLayoutsMatchPreviousGreedyOrder();
        largePlaneUsesSingleForwardCandidateScan();
    }

    private static void randomizedLayoutsMatchPreviousGreedyOrder() {
        Random random = new Random(0x52414449414E544CL);
        for (int iteration = 0; iteration < 500; iteration++) {
            List<Cell> cells = new ArrayList<>();
            int minimumFirst = random.nextInt(17) - 24;
            int minimumSecond = random.nextInt(17) - 24;
            int width = 1 + random.nextInt(15);
            int height = 1 + random.nextInt(15);
            for (int second = minimumSecond; second < minimumSecond + height; second++) {
                for (int first = minimumFirst; first < minimumFirst + width; first++) {
                    if (random.nextInt(5) != 0) {
                        cells.add(new Cell(first, second));
                    }
                }
            }
            cells.sort(Comparator.comparingInt(Cell::first).thenComparingInt(Cell::second));
            List<Rectangle> expected = previousGreedyLayout(cells);
            List<Rectangle> actual = newLayout(cells).rectangles();
            if (!actual.equals(expected)) {
                throw new AssertionError("radiant patch layout changed at randomized iteration " + iteration
                        + "\nexpected=" + expected + "\nactual=" + actual);
            }
        }
    }

    private static void largePlaneUsesSingleForwardCandidateScan() {
        int side = 256;
        List<Cell> cells = new ArrayList<>(side * side);
        for (int first = 0; first < side; first++) {
            for (int second = 0; second < side; second++) {
                cells.add(new Cell(first, second));
            }
        }
        LayoutResult result = newLayout(cells);
        int expectedRectangles = side * side / (MAXIMUM_SPAN * MAXIMUM_SPAN);
        if (result.rectangles().size() != expectedRectangles
                || result.stats().removedCells() != cells.size()
                || result.stats().firstCandidateChecks() > cells.size()) {
            throw new AssertionError("large radiant plane did not retain linear first-candidate scanning: "
                    + result.stats());
        }
    }

    private static LayoutResult newLayout(List<Cell> orderedCells) {
        Map<Long, Cell> remaining = coordinateMap(orderedCells);
        List<Rectangle> rectangles = new ArrayList<>();
        GreedyPatchMerger.MergeStats stats = GreedyPatchMerger.merge(orderedCells, Cell::first, Cell::second,
                MAXIMUM_SPAN, new GreedyPatchMerger.CellStore<>() {
                    @Override
                    public boolean contains(int firstCoordinate, int secondCoordinate) {
                        return remaining.containsKey(key(firstCoordinate, secondCoordinate));
                    }

                    @Override
                    public Cell remove(int firstCoordinate, int secondCoordinate) {
                        return remaining.remove(key(firstCoordinate, secondCoordinate));
                    }

                    @Override
                    public boolean isEmpty() {
                        return remaining.isEmpty();
                    }
                }, (first, firstCoordinate, secondCoordinate, width, height, members) ->
                        rectangles.add(new Rectangle(firstCoordinate, secondCoordinate, width, height,
                                List.copyOf(members))));
        return new LayoutResult(List.copyOf(rectangles), stats);
    }

    /** Reference copy of the removed full-map minimum scan. */
    private static List<Rectangle> previousGreedyLayout(List<Cell> cells) {
        Map<Long, Cell> remaining = coordinateMap(cells);
        List<Rectangle> rectangles = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Cell first = remaining.values().stream()
                    .min(Comparator.comparingInt(Cell::first).thenComparingInt(Cell::second))
                    .orElseThrow();
            int width = 1;
            while (width < MAXIMUM_SPAN && remaining.containsKey(key(first.first() + width, first.second()))) {
                width++;
            }
            int height = 1;
            heightLoop:
            while (height < MAXIMUM_SPAN) {
                for (int offset = 0; offset < width; offset++) {
                    if (!remaining.containsKey(key(first.first() + offset, first.second() + height))) {
                        break heightLoop;
                    }
                }
                height++;
            }
            List<Cell> members = new ArrayList<>(width * height);
            for (int v = 0; v < height; v++) {
                for (int u = 0; u < width; u++) {
                    members.add(remaining.remove(key(first.first() + u, first.second() + v)));
                }
            }
            rectangles.add(new Rectangle(first.first(), first.second(), width, height, List.copyOf(members)));
        }
        return List.copyOf(rectangles);
    }

    private static Map<Long, Cell> coordinateMap(List<Cell> cells) {
        Map<Long, Cell> result = new HashMap<>();
        for (Cell cell : cells) {
            result.put(key(cell.first(), cell.second()), cell);
        }
        return result;
    }

    private static long key(int first, int second) {
        return ((long) first << 32) | (second & 0xffffffffL);
    }

    private record Cell(int first, int second) {
    }

    private record Rectangle(int first, int second, int width, int height, List<Cell> members) {
    }

    private record LayoutResult(List<Rectangle> rectangles, GreedyPatchMerger.MergeStats stats) {
    }
}
