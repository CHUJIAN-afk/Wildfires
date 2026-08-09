package first.wildfires.tfc.calendar;

import java.util.function.LongPredicate;

/** Finds the first false-to-true event edge inside one accelerated calendar advance. */
public final class CalendarEventWindowScanner {

    public static final long SAMPLE_STEP_TICKS = 80L;

    private CalendarEventWindowScanner() {
    }

    public static ScanResult scan(long startTick, long advance, boolean previouslyMatching,
                                  LongPredicate predicate) {
        if (advance <= 0L) {
            return new ScanResult(Long.MIN_VALUE, previouslyMatching);
        }
        long endTick = Math.addExact(startTick, advance);
        long previousTick = startTick;
        boolean previous = previouslyMatching;
        while (previousTick < endTick) {
            long remaining = endTick - previousTick;
            long sampleTick = previousTick + Math.min(SAMPLE_STEP_TICKS, remaining);
            boolean matching = predicate.test(sampleTick);
            if (!previous && matching) {
                return new ScanResult(refineFirstMatch(previousTick, sampleTick, predicate), true);
            }
            previousTick = sampleTick;
            previous = matching;
        }
        return new ScanResult(Long.MIN_VALUE, previous);
    }

    private static long refineFirstMatch(long falseTick, long trueTick, LongPredicate predicate) {
        long low = falseTick;
        long high = trueTick;
        while (high - low > 1L) {
            long middle = low + (high - low) / 2L;
            if (predicate.test(middle)) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return high;
    }

    public record ScanResult(long reachedTick, boolean endingMatch) {

        public boolean found() {
            return reachedTick != Long.MIN_VALUE;
        }
    }
}
