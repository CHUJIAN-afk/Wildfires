package first.wildfires.thermal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Deterministic bounded-rectangle merger for already sorted surface cells.
 *
 * <p>The cursor only advances through the ordered input once. Cells consumed by an earlier rectangle
 * are skipped when the cursor reaches them, replacing the old full remaining-map minimum scan while
 * preserving the exact same first-cell, width-then-height, and row-major member order.</p>
 */
final class GreedyPatchMerger {

    private GreedyPatchMerger() {
    }

    static <T> MergeStats merge(List<T> orderedCells,
                                ToIntFunction<T> firstCoordinate,
                                ToIntFunction<T> secondCoordinate,
                                int maximumSpan,
                                CellStore<T> remaining,
                                RectangleConsumer<T> consumer) {
        if (maximumSpan <= 0) {
            throw new IllegalArgumentException("Maximum patch span must be positive");
        }
        int cursor = 0;
        long firstCandidateChecks = 0L;
        long containmentChecks = 0L;
        int removedCells = 0;
        int rectangles = 0;
        while (!remaining.isEmpty()) {
            T first = null;
            int firstValue = 0;
            int secondValue = 0;
            while (cursor < orderedCells.size()) {
                T candidate = orderedCells.get(cursor++);
                firstCandidateChecks++;
                int candidateFirst = firstCoordinate.applyAsInt(candidate);
                int candidateSecond = secondCoordinate.applyAsInt(candidate);
                if (remaining.contains(candidateFirst, candidateSecond)) {
                    first = candidate;
                    firstValue = candidateFirst;
                    secondValue = candidateSecond;
                    break;
                }
            }
            if (first == null) {
                throw new IllegalStateException("Patch coordinate store contains a cell absent from ordered input");
            }

            int width = 1;
            while (width < maximumSpan) {
                containmentChecks++;
                if (!remaining.contains(firstValue + width, secondValue)) {
                    break;
                }
                width++;
            }
            int height = 1;
            heightLoop:
            while (height < maximumSpan) {
                for (int offset = 0; offset < width; offset++) {
                    containmentChecks++;
                    if (!remaining.contains(firstValue + offset, secondValue + height)) {
                        break heightLoop;
                    }
                }
                height++;
            }

            List<T> members = new ArrayList<>(width * height);
            for (int v = 0; v < height; v++) {
                for (int u = 0; u < width; u++) {
                    T member = remaining.remove(firstValue + u, secondValue + v);
                    if (member == null) {
                        throw new IllegalStateException("Patch rectangle lost a previously present surface cell");
                    }
                    members.add(member);
                    removedCells++;
                }
            }
            consumer.accept(first, firstValue, secondValue, width, height, members);
            rectangles++;
        }
        return new MergeStats(firstCandidateChecks, containmentChecks, removedCells, rectangles);
    }

    interface CellStore<T> {
        boolean contains(int firstCoordinate, int secondCoordinate);

        T remove(int firstCoordinate, int secondCoordinate);

        boolean isEmpty();
    }

    @FunctionalInterface
    interface RectangleConsumer<T> {
        void accept(T first, int firstCoordinate, int secondCoordinate,
                    int width, int height, List<T> members);
    }

    record MergeStats(long firstCandidateChecks, long containmentChecks,
                      int removedCells, int rectangles) {
    }
}
