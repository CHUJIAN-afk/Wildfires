package first.wildfires.celestial;

import com.mojang.logging.LogUtils;
import first.wildfires.celestial.CelestialEventRules.RainSample;
import first.wildfires.network.TfcCalendarRateSyncPacket;
import first.wildfires.tfc.calendar.CalendarEventWindowScanner;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import java.util.Optional;
import java.util.UUID;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/** Session-only controller that restores 1x when a local natural sky event begins. */
public final class TfcCalendarEventAcceleration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_SKIP_SEARCH_DAYS = 8192L;
    private static Active active;

    private TfcCalendarEventAcceleration() {
    }

    public static StartResult start(CommandSourceStack source, CelestialEventType event, double multiplier) {
        ServerLevel level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            return StartResult.failure(Component.translatable(
                    "commands.wildfires.tfctime.until.overworld_only"));
        }
        BlockPos observer = BlockPos.containing(source.getPosition());
        Evaluation evaluation = evaluation(level, observer);
        if (event == CelestialEventType.AURORA
                && CelestialEventRules.auroraProbability(Math.abs(Math.toDegrees(
                evaluation.latitude()))) <= 0.0D) {
            return StartResult.failure(Component.translatable(
                    "commands.wildfires.tfctime.until.aurora_latitude"));
        }
        UUID playerId = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
        long calendarTick = Calendars.SERVER.getCalendarTicks();
        RainSample rain = rain(level);
        boolean matching = evaluation.matches(event, calendarTick, rain);
        active = new Active(event, multiplier, level.dimension(), observer, observer, playerId,
                matching, Long.MIN_VALUE);
        TfcCalendarRateController.setServerMultiplier(multiplier);
        new TfcCalendarRateSyncPacket(multiplier).sendToAll();
        return StartResult.success(observer, matching, event == CelestialEventType.RAINBOW);
    }

    /** Immediately moves the authoritative TFC calendar to the next deterministic event edge. */
    public static JumpResult jump(CommandSourceStack source, CelestialEventType event) {
        ServerLevel level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            return JumpResult.failure(Component.translatable(
                    "commands.wildfires.tfctime.until.overworld_only"));
        }
        if (event == CelestialEventType.RAINBOW) {
            return JumpResult.failure(Component.translatable(
                    "commands.wildfires.tfctime.skipto.rainbow_weather"));
        }
        BlockPos observer = BlockPos.containing(source.getPosition());
        Evaluation evaluation = evaluation(level, observer);
        long startTick = Calendars.SERVER.getCalendarTicks();
        if (event == CelestialEventType.AURORA
                && CelestialEventRules.auroraProbability(Math.abs(Math.toDegrees(
                evaluation.latitude()))) <= 0.0D) {
            return JumpResult.failure(Component.translatable(
                    "commands.wildfires.tfctime.until.aurora_latitude"));
        }
        long searchDays = skipSearchDays(event, evaluation);
        long requestedAdvance = searchDays * (long) CelestialMath.TICKS_IN_DAY;
        long maximumAdvance = startTick > Long.MAX_VALUE - requestedAdvance
                ? Long.MAX_VALUE - startTick : requestedAdvance;
        if (maximumAdvance <= 0L) {
            return JumpResult.failure(Component.translatable(
                    "commands.wildfires.tfctime.skipto.no_target", searchDays));
        }
        boolean currentlyMatching = evaluation.matches(event, startTick, null);
        CalendarEventWindowScanner.ScanResult scan = CalendarEventWindowScanner.scan(
                startTick, maximumAdvance, currentlyMatching,
                tick -> evaluation.matches(event, tick, null));
        if (!scan.found()) {
            return JumpResult.failure(Component.translatable(
                    "commands.wildfires.tfctime.skipto.no_target", searchDays));
        }

        long targetTick = scan.reachedTick();
        long skippedTicks = targetTick - startTick;
        active = null;
        TfcCalendarRateController.setServerMultiplier(TfcCalendarRateController.NORMAL_MULTIPLIER);
        new TfcCalendarRateSyncPacket(TfcCalendarRateController.NORMAL_MULTIPLIER).sendToAll();
        Calendars.SERVER.setTimeFromCalendarTime(targetTick);
        return JumpResult.success(observer, startTick, targetTick, skippedTicks, searchDays);
    }

    /** Called after TFC has authorized its normal one-tick calendar advance. */
    public static long limitAdvance(ServerLevel level, long startTick, long desiredAdvance) {
        Active task = active;
        if (task == null || desiredAdvance <= 0L || level.dimension() != task.dimension()) {
            return desiredAdvance;
        }
        BlockPos observer = resolveObserver(level, task);
        if (observer == null) {
            cancelAndRestore(level.getServer(), "commands.wildfires.tfctime.until.observer_lost");
            return 1L;
        }
        Evaluation evaluation = evaluation(level, observer);
        RainSample rain = rain(level);
        boolean previousMatching = task.previousMatching();
        if (!observer.equals(task.lastObserver())) {
            previousMatching = evaluation.matches(task.event(), startTick, rain);
            task = task.withObserver(observer, previousMatching);
            active = task;
        }
        Active currentTask = task;
        CalendarEventWindowScanner.ScanResult scan = CalendarEventWindowScanner.scan(
                startTick, desiredAdvance, previousMatching,
                tick -> evaluation.matches(currentTask.event(), tick, rain));
        if (scan.found()) {
            active = currentTask.withReachedTick(scan.reachedTick());
            return Math.max(1L, scan.reachedTick() - startTick);
        }
        active = currentTask.withPreviousMatching(scan.endingMatch());
        return desiredAdvance;
    }

    /** Completes only after the Mixin has applied the precisely limited calendar/dayTime advance. */
    public static void afterAdvance(ServerLevel level, long calendarTick) {
        Active task = active;
        if (task == null || task.reachedTick() != calendarTick || level.dimension() != task.dimension()) {
            return;
        }
        active = null;
        TfcCalendarRateController.setServerMultiplier(TfcCalendarRateController.NORMAL_MULTIPLIER);
        new TfcCalendarRateSyncPacket(TfcCalendarRateController.NORMAL_MULTIPLIER).sendToAll();
        Component message = Component.translatable("commands.wildfires.tfctime.until.reached",
                Component.translatable(task.event().translationKey()), calendarTick);
        notifyInitiator(level.getServer(), task.playerId(), message);
    }

    public static void onExternalCalendarJump(MinecraftServer server, long before, long after) {
        if (active == null) {
            return;
        }
        LOGGER.info("Cancelling TFC event acceleration after external calendar jump {} -> {}", before, after);
        cancelAndRestore(server, "commands.wildfires.tfctime.until.time_jump");
    }

    /** Clears only the event tracker; the caller owns the replacement multiplier and synchronization. */
    public static boolean cancelTracking() {
        boolean existed = active != null;
        active = null;
        return existed;
    }

    public static void resetSession() {
        active = null;
    }

    public static Optional<Status> status() {
        Active task = active;
        return task == null ? Optional.empty()
                : Optional.of(new Status(task.event(), task.multiplier(), task.lastObserver(),
                task.previousMatching()));
    }

    private static void cancelAndRestore(MinecraftServer server, String reasonKey) {
        Active task = active;
        if (task == null) {
            return;
        }
        active = null;
        TfcCalendarRateController.setServerMultiplier(TfcCalendarRateController.NORMAL_MULTIPLIER);
        new TfcCalendarRateSyncPacket(TfcCalendarRateController.NORMAL_MULTIPLIER).sendToAll();
        notifyInitiator(server, task.playerId(), Component.translatable(reasonKey));
    }

    private static void notifyInitiator(MinecraftServer server, UUID playerId, Component message) {
        if (playerId != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(message);
                return;
            }
        }
        server.getPlayerList().broadcastSystemMessage(message, false);
        LOGGER.info(message.getString());
    }

    private static BlockPos resolveObserver(ServerLevel level, Active task) {
        if (task.playerId() == null) {
            return task.origin();
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(task.playerId());
        return player != null && player.serverLevel() == level ? player.blockPosition() : null;
    }

    private static RainSample rain(ServerLevel level) {
        return new RainSample(level.getRainLevel(-100.0F), level.getRainLevel(0.0F),
                level.getRainLevel(100.0F));
    }

    private static Evaluation evaluation(ServerLevel level, BlockPos observer) {
        CelestialRuntimeSettings settings = CelestialConfig.serverSettings();
        int daysInMonth = Calendars.SERVER.getCalendarDaysInMonth();
        double hemisphereScale = TfeHemisphereScale.get(level);
        return new Evaluation(observer.getZ(), hemisphereScale, daysInMonth, settings);
    }

    private static long skipSearchDays(CelestialEventType event, Evaluation evaluation) {
        return skipSearchDays(event, evaluation.daysInMonth(), evaluation.settings());
    }

    static long skipSearchDays(CelestialEventType event, int daysInMonth,
                               CelestialRuntimeSettings settings) {
        if (event == CelestialEventType.RAINBOW) {
            return 0L;
        }
        double yearDays = CelestialMath.daysInYear(daysInMonth);
        CelestialRuntimeSettings.PreparedPeriods prepared = settings.preparedPeriods(daysInMonth);
        double synodicDays = prepared.synodicDays();
        double anomalisticDays = prepared.anomalisticDays();
        double requested = switch (event) {
            case NOON, MIDNIGHT -> 2.0D;
            case SUNRISE, SUNSET -> yearDays + 2.0D;
            case MOONRISE, MOONSET -> Math.max(40.0D, synodicDays * 3.0D);
            case FULL_MOON, NEW_MOON, FIRST_QUARTER, LAST_QUARTER -> synodicDays * 3.0D + 2.0D;
            case SUPERMOON -> fullMoonPerigeeBeatDays(synodicDays, anomalisticDays) * 2.0D + 2.0D;
            case SOLAR_ECLIPSE, LUNAR_ECLIPSE, BLOOD_MOON ->
                    yearDays * settings.nodalYears() + synodicDays * 2.0D + 2.0D;
            case AURORA -> MAX_SKIP_SEARCH_DAYS;
            case RAINBOW -> 0.0D;
        };
        if (!Double.isFinite(requested)) {
            return MAX_SKIP_SEARCH_DAYS;
        }
        return Math.min(MAX_SKIP_SEARCH_DAYS, Math.max(2L, (long) Math.ceil(requested)));
    }

    private static double fullMoonPerigeeBeatDays(double synodicDays, double anomalisticDays) {
        double frequencyDifference = Math.abs(1.0D / synodicDays - 1.0D / anomalisticDays);
        return frequencyDifference > 1.0E-12D && Double.isFinite(frequencyDifference)
                ? 1.0D / frequencyDifference : MAX_SKIP_SEARCH_DAYS;
    }

    /** Test seam comparing the production event-specific path with the legacy complete sample. */
    static boolean matchesAt(CelestialEventType event, long calendarTick,
                             double observerZ, double hemisphereScale, int daysInMonth,
                             CelestialRuntimeSettings settings, RainSample rain) {
        return new Evaluation(observerZ, hemisphereScale, daysInMonth, settings)
                .matches(event, calendarTick, rain);
    }

    private record Evaluation(CelestialMath.ObserverLatitudeContext observerLatitude,
                              int daysInMonth,
                              CelestialRuntimeSettings settings, double synodicDays,
                              double anomalisticDays, double sineLunarInclination) {

        private Evaluation(double observerZ, double hemisphereScale, int daysInMonth,
                           CelestialRuntimeSettings settings) {
            this(observerZ, hemisphereScale, daysInMonth, settings,
                    settings.preparedPeriods(daysInMonth));
        }

        private Evaluation(double observerZ, double hemisphereScale, int daysInMonth,
                           CelestialRuntimeSettings settings,
                           CelestialRuntimeSettings.PreparedPeriods prepared) {
            this(CelestialMath.prepareObserverLatitude(observerZ, hemisphereScale),
                    daysInMonth, settings,
                    prepared.synodicDays(), prepared.anomalisticDays(),
                    prepared.sineLunarInclination());
        }

        private CelestialMath.DaylightSample daylightAt(long calendarTick) {
            return CelestialMath.daylightSampleAt(observerLatitude, calendarTick, daysInMonth);
        }

        private CelestialMath.DisplayEventSample displayAt(long calendarTick) {
            return CelestialMath.displayEventSampleAt(observerLatitude, calendarTick,
                    daysInMonth, synodicDays, anomalisticDays, settings.nodalYears(),
                    settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale(),
                    sineLunarInclination);
        }

        private CelestialMath.QuarterEventSample quarterAt(long calendarTick) {
            return CelestialMath.quarterEventSampleAt(observerLatitude, calendarTick,
                    daysInMonth, synodicDays, anomalisticDays, settings.nodalYears(),
                    settings.lunarInclinationRadians(), settings.sunScale(), settings.moonScale(),
                    sineLunarInclination);
        }

        private double latitude() {
            return observerLatitude.latitude();
        }

        private boolean matches(CelestialEventType event, long calendarTick, RainSample rain) {
            return switch (event) {
                case NOON, MIDNIGHT -> event.matchesFractionOfDay(
                        CelestialMath.positiveModulo(calendarTick, CelestialMath.TICKS_IN_DAY)
                                / CelestialMath.TICKS_IN_DAY);
                case SUNRISE, SUNSET, AURORA, RAINBOW -> event.matches(
                        daylightAt(calendarTick), latitude(), calendarTick, rain);
                case MOONRISE, MOONSET, FULL_MOON, NEW_MOON, SUPERMOON,
                     SOLAR_ECLIPSE, LUNAR_ECLIPSE, BLOOD_MOON ->
                        event.matches(displayAt(calendarTick));
                case FIRST_QUARTER, LAST_QUARTER ->
                        event.matches(quarterAt(calendarTick));
            };
        }
    }

    private record Active(CelestialEventType event, double multiplier, ResourceKey<Level> dimension,
                          BlockPos origin, BlockPos lastObserver, UUID playerId,
                          boolean previousMatching, long reachedTick) {

        private Active withObserver(BlockPos observer, boolean matching) {
            return new Active(event, multiplier, dimension, origin, observer, playerId, matching, reachedTick);
        }

        private Active withPreviousMatching(boolean matching) {
            return new Active(event, multiplier, dimension, origin, lastObserver, playerId, matching, reachedTick);
        }

        private Active withReachedTick(long tick) {
            return new Active(event, multiplier, dimension, origin, lastObserver, playerId, true, tick);
        }
    }

    public record StartResult(boolean success, Component failure, BlockPos observer,
                              boolean waitingForNext, boolean weatherDependent) {

        private static StartResult success(BlockPos observer, boolean waitingForNext,
                                           boolean weatherDependent) {
            return new StartResult(true, Component.empty(), observer, waitingForNext, weatherDependent);
        }

        private static StartResult failure(Component failure) {
            return new StartResult(false, failure, BlockPos.ZERO, false, false);
        }
    }

    public record Status(CelestialEventType event, double multiplier, BlockPos observer,
                         boolean currentlyMatching) {
    }

    public record JumpResult(boolean success, Component failure, BlockPos observer,
                             long startTick, long targetTick, long skippedTicks, long searchedDays) {

        private static JumpResult success(BlockPos observer, long startTick, long targetTick,
                                          long skippedTicks, long searchedDays) {
            return new JumpResult(true, Component.empty(), observer, startTick, targetTick,
                    skippedTicks, searchedDays);
        }

        private static JumpResult failure(Component failure) {
            return new JumpResult(false, failure, BlockPos.ZERO, 0L, 0L, 0L, 0L);
        }
    }
}
