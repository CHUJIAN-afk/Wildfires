package first.wildfires.space.station;

/** Stable storage and station-region limits for the first space-system data version. */
public final class SpaceConstants {

    public static final int REGION_SIZE = 2_048;
    public static final int REGION_HALF_SIZE = REGION_SIZE / 2;
    public static final int BUILD_RADIUS = 768;
    public static final int REGION_SAFETY_BELT = 256;
    public static final int STATION_SAFE_Y = 128;
    public static final int MAX_WORLD_COORDINATE = 29_999_984;

    public static final int MAX_STATIONS = 4_096;
    public static final int MAX_RETIRED_REGIONS = 8_192;
    public static final int MAX_STATION_NAME_LENGTH = 64;
    public static final int MAX_MEMBERS = 128;
    public static final int MAX_DOCKS = 32;
    public static final int MAX_RETURN_CAPSULES = 64;
    public static final int MAX_LANDING_TARGETS = 64;
    public static final int MAX_AUDIT_ENTRIES = 256;
    public static final int MAX_AUDIT_DETAIL_LENGTH = 160;
    public static final int MAX_STATION_RECORD_NBT_LIST = 4_096;

    static {
        if (BUILD_RADIUS + REGION_SAFETY_BELT > REGION_HALF_SIZE) {
            throw new ExceptionInInitializerError("Station build radius and safety belt exceed region half-size");
        }
    }

    private SpaceConstants() {
    }
}
