package first.wildfires.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import first.wildfires.Wildfires;
import first.wildfires.space.celestial.CelestialRegistryRuntime;
import first.wildfires.space.celestial.CelestialSurfaceBindingResolver;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.content.StationCoreService;
import first.wildfires.space.content.StationIdTapeItem;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import first.wildfires.space.station.StationService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

/** Permission-2 development commands that only call the authoritative station service. */
public final class SpaceStationCommand {

    private SpaceStationCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createWildfiresBranch() {
        return Commands.literal("space")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("station")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(SpaceStationCommand::create)))
                        .then(Commands.literal("list").executes(SpaceStationCommand::list))
                        .then(Commands.literal("info")
                                .then(Commands.argument("station", UuidArgument.uuid())
                                        .suggests(SpaceStationCommand::suggestStations)
                                        .executes(SpaceStationCommand::info)))
                        .then(Commands.literal("tape")
                                .then(Commands.argument("station", UuidArgument.uuid())
                                        .suggests(SpaceStationCommand::suggestStations)
                                        .executes(SpaceStationCommand::tape)))
                        .then(Commands.literal("teleport")
                                .then(Commands.argument("station", UuidArgument.uuid())
                                        .suggests(SpaceStationCommand::suggestStations)
                                        .executes(SpaceStationCommand::teleport)))
                        .then(Commands.literal("recover")
                                .then(Commands.argument("station", UuidArgument.uuid())
                                        .suggests(SpaceStationCommand::suggestStations)
                                        .executes(SpaceStationCommand::recover))))
                .then(Commands.literal("returncapsule")
                        .then(Commands.literal("info")
                                .then(Commands.argument("capsule", EntityArgument.entity())
                                        .executes(SpaceStationCommand::capsuleInfo)))
                        .then(Commands.literal("recover")
                                .then(Commands.argument("capsule", EntityArgument.entity())
                                        .executes(SpaceStationCommand::capsuleRecover))));
    }

    private static int create(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal("A player is required to own a new station"));
            return 0;
        }
        var earth = Wildfires.rl("earth");
        var surface = CelestialSurfaceBindingResolver.resolve(source.getServer(), earth).orElse(null);
        if (surface == null || !surface.dimension().equals(net.minecraft.world.level.Level.OVERWORLD.location())) {
            source.sendFailure(Component.literal("Cannot create an Earth test station: "
                    + "wildfires:earth is not bound to the loaded minecraft:overworld"));
            return 0;
        }
        StationService.OperationResult result = StationService.create(
                SpaceSavedData.get(source.getServer()), UUID.randomUUID(),
                StringArgumentType.getString(context, "name"), player.getUUID(),
                earth, CelestialRegistryRuntime.current(),
                source.getServer().overworld().getGameTime());
        result.station().ifPresent(station -> StationCoreService.ensureCore(source.getServer(), station));
        return sendResult(source, result);
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        SpaceSavedData data = SpaceSavedData.get(source.getServer());
        if (!data.writable()) {
            source.sendFailure(Component.literal(data.writeBlockReason().orElse("Space data is read-only")));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Stations: " + data.stations().size()
                + ", retired regions: " + data.retiredRegions().size()), false);
        data.stations().values().stream()
                .sorted(java.util.Comparator.comparing(StationRecord::name)
                        .thenComparing(value -> value.stationId().toString()))
                .forEach(station -> source.sendSuccess(() -> Component.literal(
                        station.stationId() + " " + station.name() + " body=" + station.currentBody()
                                + " status=" + station.status().id() + " revision=" + station.revision()), false));
        return Command.SINGLE_SUCCESS;
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID stationId = UuidArgument.getUuid(context, "station");
        StationRecord station = SpaceSavedData.get(source.getServer()).station(stationId).orElse(null);
        if (station == null) {
            source.sendFailure(Component.literal("Unknown station: " + stationId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(station.stationId() + " " + station.name()
                + " owner=" + station.owner() + " region=" + station.region().gridX() + ","
                + station.region().gridZ() + " body=" + station.currentBody() + " status="
                + station.status().id() + " journey=" + station.journey().map(value -> value.phase().id())
                .orElse("orbiting") + " revision=" + station.revision()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int teleport(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID stationId = UuidArgument.getUuid(context, "station");
        return StationService.safePoint(SpaceSavedData.get(source.getServer()), stationId)
                .map(position -> {
                    ServerPlayer player;
                    try {
                        player = source.getPlayerOrException();
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                        source.sendFailure(Component.literal("A player is required for station teleport"));
                        return 0;
                    }
                    var orbit = source.getServer().getLevel(SpaceDimensions.ORBIT);
                    if (orbit == null) {
                        source.sendFailure(Component.literal("Required dimension wildfires:orbit is unavailable; "
                                + "no overworld fallback was used"));
                        return 0;
                    }
                    player.teleportTo(orbit, position.getX() + 0.5D, position.getY(),
                            position.getZ() + 0.5D, player.getYRot(), player.getXRot());
                    player.setDeltaMovement(0.0D, 0.0D, 0.0D);
                    source.sendSuccess(() -> Component.literal("Teleported to station " + stationId
                            + " in wildfires:orbit at " + position.toShortString()), false);
                    return Command.SINGLE_SUCCESS;
                })
                .orElseGet(() -> {
                    source.sendFailure(Component.literal("Unknown station: " + stationId));
                    return 0;
                });
    }

    private static int tape(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID stationId = UuidArgument.getUuid(context, "station");
        StationRecord station = SpaceSavedData.get(source.getServer()).station(stationId).orElse(null);
        if (station == null) {
            source.sendFailure(Component.literal("Unknown station: " + stationId));
            return 0;
        }
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal("A player is required to receive a station ID tape"));
            return 0;
        }
        var tape = StationIdTapeItem.createProgrammed(station);
        if (!player.getInventory().add(tape)) player.drop(tape, false);
        source.sendSuccess(() -> Component.literal("Issued station ID tape for "
                + station.name() + " (" + station.stationId() + ")"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int recover(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID stationId = UuidArgument.getUuid(context, "station");
        UUID actor = source.getEntity() == null ? new UUID(0L, 0L) : source.getEntity().getUUID();
        StationService.OperationResult result = StationService.recover(
                SpaceSavedData.get(source.getServer()), stationId, actor, true,
                CelestialRegistryRuntime.current(), source.getServer().overworld().getGameTime());
        return sendResult(source, result);
    }

    private static int capsuleInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        net.minecraft.world.entity.Entity entity;
        try {
            entity = EntityArgument.getEntity(context, "capsule");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal(exception.getRawMessage().getString()));
            return 0;
        }
        if (!(entity instanceof first.wildfires.space.capsule.ReusableReturnCapsuleEntity capsule)) {
            source.sendFailure(Component.literal("Selected entity is not a reusable return capsule"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("capsule=" + capsule.getUUID()
                + " state=" + capsule.capsuleState() + " fuel=" + capsule.fuelMb() + "/4000mB"
                + " station=" + capsule.stationId().map(UUID::toString).orElse("none")
                + " revision=" + capsule.revision() + " ticket="
                + capsule.transitionTicket().map(value -> value.ticketId() + "/" + value.stage())
                .orElse("none")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestStations(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        SpaceSavedData.get(context.getSource().getServer()).stations().values().stream()
                .sorted(Comparator.comparing(StationRecord::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(station -> station.stationId().toString()))
                .filter(station -> station.stationId().toString().startsWith(remaining)
                        || station.name().toLowerCase(java.util.Locale.ROOT).contains(remaining))
                .forEach(station -> builder.suggest(station.stationId().toString(),
                        Component.literal(station.name())));
        return builder.buildFuture();
    }

    private static int capsuleRecover(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        net.minecraft.world.entity.Entity entity;
        try {
            entity = EntityArgument.getEntity(context, "capsule");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal(exception.getRawMessage().getString()));
            return 0;
        }
        if (!(entity instanceof first.wildfires.space.capsule.ReusableReturnCapsuleEntity capsule)) {
            source.sendFailure(Component.literal("Selected entity is not a reusable return capsule"));
            return 0;
        }
        var result = first.wildfires.space.capsule.ReturnCapsuleService.recoverTransaction(capsule);
        if (!result.successful()) {
            source.sendFailure(Component.translatable(result.translationKey()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(result.translationKey()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int sendResult(CommandSourceStack source, StationService.OperationResult result) {
        if (!result.successful()) {
            source.sendFailure(Component.literal(result.status() + ": " + result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()
                + result.station().map(station -> ": " + station.stationId()).orElse("")), true);
        return Command.SINGLE_SUCCESS;
    }
}
