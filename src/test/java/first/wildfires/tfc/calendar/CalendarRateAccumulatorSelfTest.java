package first.wildfires.tfc.calendar;

import first.wildfires.network.TfcCalendarRateSyncPacket;
import first.wildfires.celestial.CelestialEventRules;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/** Plain-Java regression checks for TFC calendar-rate accumulation. */
public final class CalendarRateAccumulatorSelfTest {

    private CalendarRateAccumulatorSelfTest() {
    }

    public static void main(String[] args) {
        integerRatesAdvanceExactly();
        fractionalRatesCarryWithoutChangingTickCount();
        pauseAndNoBaseAdvanceStayStopped();
        changingAndClearingRateDiscardOldCarry();
        synchronizationPacketRoundTripsExactly();
        invalidRatesAreRejected();
        acceleratedIntervalsCannotSkipEventWindows();
        activeEventsWaitForTheirNextNaturalOccurrence();
        auroraAndRainbowRulesAreDeterministicAndNatural();
        System.out.println("CalendarRateAccumulatorSelfTest: all checks passed");
    }

    private static void integerRatesAdvanceExactly() {
        CalendarRateAccumulator accumulator = new CalendarRateAccumulator();
        accumulator.setMultiplier(1200.0D);
        for (int tick = 0; tick < 40; tick++) {
            assertEquals(1200L, accumulator.calendarTicksForBaseAdvance(true), "1200x tick");
        }
    }

    private static void fractionalRatesCarryWithoutChangingTickCount() {
        CalendarRateAccumulator accumulator = new CalendarRateAccumulator();
        accumulator.setMultiplier(20.0D / 24.0D);
        long calendarTicks = 0L;
        for (int playerTick = 0; playerTick < 24_000; playerTick++) {
            calendarTicks += accumulator.calendarTicksForBaseAdvance(true);
        }
        assertEquals(20_000L, calendarTicks, "24-minute TFC 1.21 day-length preset");
    }

    private static void pauseAndNoBaseAdvanceStayStopped() {
        CalendarRateAccumulator accumulator = new CalendarRateAccumulator();
        accumulator.setMultiplier(0.0D);
        assertEquals(0L, accumulator.calendarTicksForBaseAdvance(true), "disabled calendar");
        accumulator.setMultiplier(1200.0D);
        assertEquals(0L, accumulator.calendarTicksForBaseAdvance(false), "paused TFC base calendar");
    }

    private static void changingAndClearingRateDiscardOldCarry() {
        CalendarRateAccumulator accumulator = new CalendarRateAccumulator();
        accumulator.setMultiplier(0.5D);
        assertEquals(0L, accumulator.calendarTicksForBaseAdvance(true), "fractional carry setup");
        accumulator.setMultiplier(1.0D);
        assertEquals(1L, accumulator.calendarTicksForBaseAdvance(true), "clear to normal");
    }

    private static void invalidRatesAreRejected() {
        for (double invalid : new double[]{-1.0D, 1200.0001D, Double.NaN, Double.POSITIVE_INFINITY}) {
            try {
                CalendarRateAccumulator.validateMultiplier(invalid);
                throw new AssertionError("invalid multiplier accepted: " + invalid);
            } catch (IllegalArgumentException expected) {
                // Expected.
            }
        }
    }

    private static void synchronizationPacketRoundTripsExactly() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new TfcCalendarRateSyncPacket(123.456D).encode(buffer);
        TfcCalendarRateSyncPacket decoded = new TfcCalendarRateSyncPacket(buffer);
        if (Double.doubleToLongBits(decoded.multiplier()) != Double.doubleToLongBits(123.456D)
                || buffer.readableBytes() != 0) {
            throw new AssertionError("calendar-rate packet did not round-trip exactly");
        }
        buffer.release();
    }

    private static void acceleratedIntervalsCannotSkipEventWindows() {
        CalendarEventWindowScanner.ScanResult result = CalendarEventWindowScanner.scan(
                0L, 1200L, false, tick -> tick >= 503L && tick <= 900L);
        assertEquals(503L, result.reachedTick(), "1200x event entry");
        if (!result.found() || !result.endingMatch()) {
            throw new AssertionError("accelerated interval did not retain the detected event edge");
        }
    }

    private static void activeEventsWaitForTheirNextNaturalOccurrence() {
        CalendarEventWindowScanner.ScanResult result = CalendarEventWindowScanner.scan(
                0L, 1200L, true,
                tick -> tick <= 100L || tick >= 703L && tick <= 900L);
        assertEquals(703L, result.reachedTick(), "next event after an already-active window");
    }

    private static void auroraAndRainbowRulesAreDeterministicAndNatural() {
        if (CelestialEventRules.auroraProbability(49.999D) != 0.0D
                || CelestialEventRules.auroraProbability(65.0D) != 0.42D) {
            throw new AssertionError("aurora latitude probabilities changed");
        }
        long key = CelestialEventRules.auroraEventKey(123456L, Math.toRadians(70.0D));
        if (key != CelestialEventRules.auroraEventKey(123456L, Math.toRadians(70.0D))
                || !Double.isFinite(CelestialEventRules.auroraRoll(key))) {
            throw new AssertionError("aurora schedule was not deterministic and finite");
        }
        if (!CelestialEventRules.startsRainbow(0.8F, 0.4F, 0.2F, 0.25D, 0.5D)
                || CelestialEventRules.startsRainbow(0.2F, 0.4F, 0.8F, 0.25D, 0.5D)
                || CelestialEventRules.startsRainbow(0.8F, 0.4F, 0.2F, 0.75D, 0.5D)) {
            throw new AssertionError("rainbow targeting stopped matching the natural rain/daylight rule");
        }
    }

    private static void assertEquals(long expected, long actual, String name) {
        if (expected != actual) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}
