package first.wildfires.client;

/** Client-session switch for the upper-left thermal diagnostic readout. */
public final class ThermalHudState {

    private static boolean enabled;

    private ThermalHudState() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static boolean setEnabled(boolean value) {
        enabled = value;
        return enabled;
    }

    public static void reset() {
        enabled = false;
    }
}
