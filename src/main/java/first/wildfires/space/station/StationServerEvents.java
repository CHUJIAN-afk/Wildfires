package first.wildfires.space.station;

import first.wildfires.command.SpaceStationCommand;
import first.wildfires.space.celestial.CelestialRegistryRuntime;
import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Forge-bus registration for station commands and definition-reload reconciliation. */
public final class StationServerEvents {

    private static boolean registered;

    private StationServerEvents() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(StationServerEvents::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(StationServerEvents::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(StationJourneyTicker::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(StationJourneyTicker::onServerStopped);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wildfires")
                .then(SpaceStationCommand.createWildfiresBranch()));
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        var server = event.getPlayerList().getServer();
        StationService.reconcileDefinitions(SpaceSavedData.get(server),
                CelestialRegistryRuntime.current(), server.overworld().getGameTime());
    }
}
