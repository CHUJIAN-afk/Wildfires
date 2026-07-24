package first.wildfires.thermal;

/** Client-side copy of the server-authoritative local thermal offset. */
public final class ClientThermalState {

    private static float localOffset;

    private ClientThermalState() {
    }

    public static float getLocalOffset() {
        return localOffset;
    }

    public static void setLocalOffset(float offset) {
        localOffset = offset;
    }
}
