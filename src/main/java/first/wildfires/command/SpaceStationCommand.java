package first.wildfires.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import first.wildfires.Wildfires;
import first.wildfires.space.celestial.CelestialRegistryRuntime;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import first.wildfires.space.station.StationService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

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
                                        .executes(SpaceStationCommand::info)))
                        .then(Commands.literal("teleport")
                                .then(Commands.argument("station", UuidArgument.uuid())
                                        .executes(SpaceStationCommand::teleport)))
                        .then(Commands.literal("recover")
                                .then(Commands.argument("station", UuidArgument.uuid())
                                        .executes(SpaceStationCommand::recover))));
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
        StationService.OperationResult result = StationService.create(
                SpaceSavedData.get(source.getServer()), UUID.randomUUID(),
                StringArgumentType.getString(context, "name"), player.getUUID(),
                Wildfires.rl("earth"), CelestialRegistryRuntime.current(),
                source.getServer().overworld().getGameTime());
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

    private static int recover(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID stationId = UuidArgument.getUuid(context, "station");
        UUID actor = source.getEntity() == null ? new UUID(0L, 0L) : source.getEntity().getUUID();
        StationService.OperationResult result = StationService.recover(
                SpaceSavedData.get(source.getServer()), stationId, actor, true,
                CelestialRegistryRuntime.current(), source.getServer().overworld().getGameTime());
        return sendResult(source, result);
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
