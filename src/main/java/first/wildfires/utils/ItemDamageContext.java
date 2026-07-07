package first.wildfires.utils;

public class ItemDamageContext {
    private static final ThreadLocal<Boolean> INSTANT_BLOCK_BREAK = ThreadLocal.withInitial(() -> false);

    public static void setInstantBlockBreak(boolean value) {
        INSTANT_BLOCK_BREAK.set(value);
    }

    public static boolean isInstantBlockBreak() {
        return INSTANT_BLOCK_BREAK.get();
    }

    public static void clearInstantBlockBreak() {
        INSTANT_BLOCK_BREAK.remove();
    }
}
