package first.wildfires.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import first.wildfires.celestial.CelestialEventType;
import first.wildfires.celestial.CelestialMath;
import first.wildfires.celestial.TfcCalendarEventAcceleration;
import first.wildfires.network.TfcCalendarRateSyncPacket;
import first.wildfires.tfc.calendar.CalendarRateAccumulator;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Server commands for changing TFC calendar speed without changing the server tick rate. */
public final class TfcTimeCommand {

    private static final double VANILLA_DAY_LENGTH_MINUTES = 20.0D;
    private static final int TFC_1_21_DEFAULT_DAY_LENGTH_MINUTES = 24;
    private static final double DEFAULT_EVENT_MULTIPLIER = 1200.0D;

    private TfcTimeCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createWildfiresBranch() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tfctime")
                .requires(source -> source.hasPermission(2))
                .executes(TfcTimeCommand::query)
                .then(Commands.literal("query").executes(TfcTimeCommand::query))
                .then(Commands.literal("set")
                        .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(
                                        CalendarRateAccumulator.MIN_MULTIPLIER,
                                        CalendarRateAccumulator.MAX_MULTIPLIER))
                                .executes(context -> setMultiplier(context,
                                        DoubleArgumentType.getDouble(context, "multiplier"), false))))
                .then(createUntilBranch())
                .then(createSkipToBranch())
                .then(Commands.literal("clear").executes(TfcTimeCommand::clear))
                .then(Commands.literal("reset").executes(TfcTimeCommand::clear));

        addPreset(root, "normal", 1.0D);
        addPreset(root, "5x", 5.0D);
        addPreset(root, "20x", 20.0D);
        addPreset(root, "60x", 60.0D);
        addPreset(root, "120x", 120.0D);
        addPreset(root, "1200x", 1200.0D);
        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createUntilBranch() {
        LiteralArgumentBuilder<CommandSourceStack> until = Commands.literal("until")
                .executes(TfcTimeCommand::queryUntil)
                .then(Commands.literal("cancel").executes(TfcTimeCommand::clear));
        for (CelestialEventType event : CelestialEventType.values()) {
            until.then(Commands.literal(event.commandName())
                    .executes(context -> startUntil(context, event, DEFAULT_EVENT_MULTIPLIER))
                    .then(Commands.argument("speed", DoubleArgumentType.doubleArg(1.0D,
                                    CalendarRateAccumulator.MAX_MULTIPLIER))
                            .executes(context -> startUntil(context, event,
                                    DoubleArgumentType.getDouble(context, "speed")))));
        }
        return until;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createSkipToBranch() {
        LiteralArgumentBuilder<CommandSourceStack> skipTo = Commands.literal("skipto");
        for (CelestialEventType event : CelestialEventType.values()) {
            skipTo.then(Commands.literal(event.commandName())
                    .executes(context -> skipTo(context, event)));
        }
        return skipTo;
    }

    /** Mirrors TFC 1.21's /time set dayLength branch on the fixed TFC 3.2.20 runtime. */
    public static LiteralArgumentBuilder<CommandSourceStack> createTimeBranch() {
        return Commands.literal("time")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.literal("dayLength")
                                .then(Commands.literal("vanilla")
                                        .executes(context -> setDayLength(context, 20)))
                                .then(Commands.literal("default")
                                        .executes(context -> setDayLength(context,
                                                TFC_1_21_DEFAULT_DAY_LENGTH_MINUTES)))
                                .then(Commands.literal("disabled")
                                        .executes(context -> setMultiplier(context, 0.0D, false)))
                                .then(Commands.literal("realtime")
                                        .executes(context -> setDayLength(context, 24 * 60)))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                        .executes(context -> setDayLength(context,
                                                IntegerArgumentType.getInteger(context, "minutes"))))));
    }

    private static void addPreset(LiteralArgumentBuilder<CommandSourceStack> root,
                                  String literal, double multiplier) {
        root.then(Commands.literal(literal)
                .executes(context -> setMultiplier(context, multiplier, false)));
    }

    private static int setDayLength(CommandContext<CommandSourceStack> context, int minutes) {
        return setMultiplier(context, VANILLA_DAY_LENGTH_MINUTES / minutes, false);
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        return setMultiplier(context, TfcCalendarRateController.NORMAL_MULTIPLIER, true);
    }

    private static int setMultiplier(CommandContext<CommandSourceStack> context,
                                     double multiplier, boolean cleared) {
        TfcCalendarEventAcceleration.cancelTracking();
        TfcCalendarRateController.setServerMultiplier(multiplier);
        new TfcCalendarRateSyncPacket(multiplier).sendToAll();
        context.getSource().sendSuccess(() -> Component.translatable(
                cleared ? "commands.wildfires.tfctime.cleared" : "commands.wildfires.tfctime.set",
                formatMultiplier(multiplier), formatDayLength(multiplier)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int query(CommandContext<CommandSourceStack> context) {
        double multiplier = TfcCalendarRateController.serverMultiplier();
        context.getSource().sendSuccess(() -> Component.translatable("commands.wildfires.tfctime.query",
                formatMultiplier(multiplier), formatDayLength(multiplier)), false);
        sendUntilStatus(context.getSource());
        return Command.SINGLE_SUCCESS;
    }

    private static int startUntil(CommandContext<CommandSourceStack> context,
                                  CelestialEventType event, double multiplier) {
        TfcCalendarEventAcceleration.StartResult result =
                TfcCalendarEventAcceleration.start(context.getSource(), event, multiplier);
        if (!result.success()) {
            context.getSource().sendFailure(result.failure());
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.wildfires.tfctime.until.started", Component.translatable(event.translationKey()),
                formatMultiplier(multiplier), result.observer().getX(), result.observer().getY(),
                result.observer().getZ()), true);
        if (result.waitingForNext()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.wildfires.tfctime.until.waiting_next",
                    Component.translatable(event.translationKey())), false);
        }
        if (result.weatherDependent()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.wildfires.tfctime.until.rainbow_weather"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int queryUntil(CommandContext<CommandSourceStack> context) {
        sendUntilStatus(context.getSource());
        return Command.SINGLE_SUCCESS;
    }

    private static int skipTo(CommandContext<CommandSourceStack> context, CelestialEventType event) {
        TfcCalendarEventAcceleration.JumpResult result =
                TfcCalendarEventAcceleration.jump(context.getSource(), event);
        if (!result.success()) {
            context.getSource().sendFailure(result.failure());
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.wildfires.tfctime.skipto.reached",
                Component.translatable(event.translationKey()), formatCalendarDays(result.skippedTicks()),
                result.skippedTicks(), result.targetTick()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendUntilStatus(CommandSourceStack source) {
        TfcCalendarEventAcceleration.status().ifPresentOrElse(status ->
                        source.sendSuccess(() -> Component.translatable(
                                "commands.wildfires.tfctime.until.status",
                                Component.translatable(status.event().translationKey()),
                                formatMultiplier(status.multiplier()), status.observer().getX(),
                                status.observer().getY(), status.observer().getZ()), false),
                () -> source.sendSuccess(() -> Component.translatable(
                        "commands.wildfires.tfctime.until.none"), false));
    }

    private static String formatMultiplier(double multiplier) {
        return String.format(Locale.ROOT, "%.4g", multiplier);
    }

    private static String formatDayLength(double multiplier) {
        return multiplier == 0.0D
                ? "∞"
                : String.format(Locale.ROOT, "%.4g", VANILLA_DAY_LENGTH_MINUTES / multiplier);
    }

    private static String formatCalendarDays(long calendarTicks) {
        return String.format(Locale.ROOT, "%.6f", calendarTicks / CelestialMath.TICKS_IN_DAY);
    }
}
