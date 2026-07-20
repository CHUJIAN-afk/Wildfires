package first.wildfires.diagnostics;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Locale;

/** Records a compact startup timeline in the normal game log. */
public final class StartupDiagnostics {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MOD_LOAD_STARTED_AT = System.nanoTime();

    private StartupDiagnostics() {
    }

    public static void commonMark(String stage) {
        mark("common", stage);
    }

    public static void clientMark(String stage) {
        mark("client", stage);
    }

    public static void serverMark(String stage) {
        mark("server", stage);
    }

    public static void clientCompleted(String stage, long startedAt) {
        completed("client", stage, startedAt);
    }

    public static void serverCompleted(String stage, long startedAt) {
        completed("server", stage, startedAt);
    }

    private static void mark(String side, String stage) {
        LOGGER.info("[Wildfires startup/{}] {} at {} ms since Wildfires loaded", side, stage, elapsedMillis(MOD_LOAD_STARTED_AT));
    }

    public static long now() {
        return System.nanoTime();
    }

    private static void completed(String side, String stage, long startedAt) {
        LOGGER.info("[Wildfires startup/{}] {} completed in {} ms", side, stage, elapsedMillis(startedAt));
    }

    public static String elapsedSeconds(long startedAt) {
        return String.format(Locale.ROOT, "%.3f", (System.nanoTime() - startedAt) / 1_000_000_000D);
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
