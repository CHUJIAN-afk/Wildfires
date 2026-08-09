package first.wildfires.tfc.calendar;

/**
 * Converts one real TFC calendar advance into a configurable number of calendar ticks.
 * The fractional carry mirrors the calendarPartialTick behavior introduced by TFC 1.21.
 */
public final class CalendarRateAccumulator {

    public static final double MIN_MULTIPLIER = 0.0D;
    public static final double MAX_MULTIPLIER = 1200.0D;

    private double multiplier = 1.0D;
    private double carry;

    public synchronized void setMultiplier(double multiplier) {
        validateMultiplier(multiplier);
        this.multiplier = multiplier;
        carry = 0.0D;
    }

    public synchronized double multiplier() {
        return multiplier;
    }

    public synchronized long calendarTicksForBaseAdvance(boolean baseAdvanced) {
        if (!baseAdvanced) {
            return 0L;
        }
        carry += multiplier;
        long calendarTicks = (long) Math.floor(carry);
        carry -= calendarTicks;
        return calendarTicks;
    }

    public static void validateMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier)
                || multiplier < MIN_MULTIPLIER
                || multiplier > MAX_MULTIPLIER) {
            throw new IllegalArgumentException("TFC calendar multiplier must be finite and between "
                    + MIN_MULTIPLIER + " and " + MAX_MULTIPLIER + ": " + multiplier);
        }
    }
}
