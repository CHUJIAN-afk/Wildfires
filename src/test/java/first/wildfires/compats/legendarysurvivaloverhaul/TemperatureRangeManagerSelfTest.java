package first.wildfires.compats.legendarysurvivaloverhaul;

/** Pure validation checks for KubeJS-defined LSO band topology. */
public final class TemperatureRangeManagerSelfTest {

    private TemperatureRangeManagerSelfTest() {
    }

    public static void main(String[] args) {
        assertValid(new int[]{0, 8, 8, 15, 15, 25, 25, 32, 32, 40});
        assertValid(new int[]{0, 2, 2, 9, 9, 30, 30, 44, 44, 55});
        assertInvalid(new int[]{0, 8, 9, 15, 15, 25, 25, 32, 32, 40}, "gap");
        assertInvalid(new int[]{0, 8, 7, 15, 15, 25, 25, 32, 32, 40}, "overlap");
        assertInvalid(new int[]{0, 8, 8, 15, 15, 15, 15, 32, 32, 40}, "empty band");
        assertInvalid(new int[]{-20, 0, 0, 9, 9, 30, 30, 44, 44, 55}, "negative player minimum");
        assertInvalid(new int[]{Integer.MIN_VALUE, -8, -8, 0, 0, 8, 8, 16, 16, Integer.MAX_VALUE},
                "overflowing total span");
        assertInvalid(new int[]{1_500_000_000, 1_500_000_001, 1_500_000_001, 1_500_000_002,
                1_500_000_002, 1_500_000_003, 1_500_000_003, 1_500_000_004,
                1_500_000_004, 1_500_000_005}, "overflowing midpoint");
        assertInvalid(new int[]{0, 10}, "wrong bound count");
        assertInvalid(null, "null bounds");
        System.out.println("TemperatureRangeManagerSelfTest: all checks passed");
    }

    private static void assertValid(int[] bounds) {
        if (!TemperatureRangeManager.isValidBounds(bounds)) {
            throw new AssertionError("Expected valid temperature ranges");
        }
    }

    private static void assertInvalid(int[] bounds, String label) {
        if (TemperatureRangeManager.isValidBounds(bounds)) {
            throw new AssertionError("Expected invalid temperature ranges: " + label);
        }
    }
}
