package first.wildfires.tfc.calendar;

/** Holds the independent server-authoritative and client-interpolation calendar rates. */
public final class TfcCalendarRateController {

    public static final double NORMAL_MULTIPLIER = 1.0D;
    private static final CalendarRateAccumulator SERVER = new CalendarRateAccumulator();
    private static final CalendarRateAccumulator CLIENT = new CalendarRateAccumulator();

    private TfcCalendarRateController() {
    }

    public static void setServerMultiplier(double multiplier) {
        SERVER.setMultiplier(multiplier);
    }

    public static double serverMultiplier() {
        return SERVER.multiplier();
    }

    public static long serverCalendarTicksForBaseAdvance(boolean baseAdvanced) {
        return SERVER.calendarTicksForBaseAdvance(baseAdvanced);
    }

    public static void acceptClientMultiplier(double multiplier) {
        CLIENT.setMultiplier(multiplier);
    }

    public static double clientMultiplier() {
        return CLIENT.multiplier();
    }

    /** Calendar-tick interpolation for one rendered client tick at the synchronized TFC rate. */
    public static double clientPartialCalendarTicks(float partialTick) {
        if (!Float.isFinite(partialTick)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, partialTick)) * CLIENT.multiplier();
    }

    public static long clientCalendarTicksForBaseAdvance(boolean baseAdvanced) {
        return CLIENT.calendarTicksForBaseAdvance(baseAdvanced);
    }

    public static void resetServer() {
        SERVER.setMultiplier(NORMAL_MULTIPLIER);
    }

    public static void resetClient() {
        CLIENT.setMultiplier(NORMAL_MULTIPLIER);
    }
}
