package first.wildfires.space.station;

import first.wildfires.command.SpaceStationCommand;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.celestial.CelestialRegistryRuntime;
import first.wildfires.space.content.SpaceContentRegister;
import first.wildfires.space.content.StationCoreService;
import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TickEvent;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Forge-bus registration for station commands and definition-reload reconciliation. */
public final class StationServerEvents {

    private static final int CORE_AUDIT_INTERVAL_TICKS = 20;
    private static final Set<UUID> CORE_REPAIR_QUEUE = new LinkedHashSet<>();
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
        MinecraftForge.EVENT_BUS.addListener(StationServerEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(StationServerEvents::onBreakBlock);
        MinecraftForge.EVENT_BUS.addListener(StationServerEvents::onEntityMount);
        MinecraftForge.EVENT_BUS.addListener(StationServerEvents::onServerTick);
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

    private static void onServerStarted(ServerStartedEvent event) {
        StationCoreService.ensureAll(event.getServer());
    }

    private static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (StationCoreService.isCoreStructureBlock(event.getState())) {
            event.setCanceled(true);
        }
    }

    private static void onEntityMount(EntityMountEvent event) {
        if (event.isDismounting()
                && event.getEntityBeingMounted() instanceof first.wildfires.space.capsule.ReusableReturnCapsuleEntity capsule
                && !capsule.capsuleState().interactive()
                && !capsule.transferDismountInProgress()) {
            event.setCanceled(true);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long gameTime = event.getServer().overworld().getGameTime();
        if (gameTime % CORE_AUDIT_INTERVAL_TICKS != 0L) {
            return;
        }
        var data = SpaceSavedData.get(event.getServer());
        var orbit = event.getServer().getLevel(SpaceDimensions.ORBIT);
        if (!data.writable() || orbit == null) {
            return;
        }
        // Auditing twenty times less often than entity/world ticks is enough to make command/mod
        // replacement temporary without imposing a per-station per-tick block lookup.
        CORE_REPAIR_QUEUE.addAll(data.stations().keySet());
        Iterator<UUID> iterator = CORE_REPAIR_QUEUE.iterator();
        while (iterator.hasNext()) {
            UUID stationId = iterator.next();
            iterator.remove();
            data.station(stationId).filter(station -> orbit.hasChunkAt(station.primaryDock().position()))
                    .ifPresent(station -> StationCoreService.ensureCore(event.getServer(), station));
        }
    }
}
