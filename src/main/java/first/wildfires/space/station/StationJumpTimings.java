package first.wildfires.space.station;

/** Fixed subjective-time contract for a relativistic station jump (20 ticks per second). */
public final class StationJumpTimings {

    public static final long ACCELERATION_TICKS = 60L;
    public static final long CRUISE_TICKS = 160L;
    public static final long DECELERATION_TICKS = 60L;

    private StationJumpTimings() {
    }
}
