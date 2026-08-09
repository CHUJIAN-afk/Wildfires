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
                evaluation.at(Calendars.SERVER.getCalendarTicks()).latitude()))) <= 0.0D) {
            return StartResult.failure(Component.translatable(
                    "commands.wildfires.tfctime.until.aurora_latitude"));
        }
        UUID playerId = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
        long calendarTick = Calendars.SERVER.getCalendarTicks();
        RainSample rain = rain(level);
        boolean matching = event.matches(evaluation.at(calendarTick), calendarTick, rain);
        active = new Active(event, multiplier, level.dimension(), observer, observer, playerId,
                matching, Long.MIN_VALUE);
        TfcCalendarRateController.setServerMultiplier(multiplier);
        new TfcCalendarRateSyncPacket(multiplier).sendToAll();
        return StartResult.success(observer, matching, event == CelestialEventType.RAINBOW);
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
            previousMatching = task.event().matches(evaluation.at(startTick), startTick, rain);
            task = task.withObserver(observer, previousMatching);
            active = task;
        }
        Active currentTask = task;
        CalendarEventWindowScanner.ScanResult scan = CalendarEventWindowScanner.scan(
                startTick, desiredAdvance, previousMatching,
                tick -> currentTask.event().matches(evaluation.at(tick), tick, rain));
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

    private record Evaluation(double observerZ, double hemisphereScale, int daysInMonth,
                              CelestialRuntimeSettings settings) {

        private CelestialMath.Result at(long calendarTick) {
            return CelestialMath.calculate(new CelestialMath.Input(observerZ, hemisphereScale, calendarTick,
                    daysInMonth, settings.resolvedSynodicDays(daysInMonth),
                    settings.resolvedAnomalisticDays(daysInMonth), settings.nodalYears(),
                    settings.lunarInclinationRadians()));
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
}
