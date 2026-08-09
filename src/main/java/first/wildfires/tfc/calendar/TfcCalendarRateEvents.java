package first.wildfires.tfc.calendar;

import first.wildfires.Wildfires;
import first.wildfires.command.TfcTimeCommand;
import first.wildfires.celestial.TfcCalendarEventAcceleration;
import first.wildfires.network.TfcCalendarRateSyncPacket;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers commands, resets session-only state, and synchronizes joining players. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TfcCalendarRateEvents {

    private TfcCalendarRateEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wildfires")
                .then(TfcTimeCommand.createWildfiresBranch()));
        event.getDispatcher().register(TfcTimeCommand.createTimeBranch());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        TfcCalendarEventAcceleration.resetSession();
        TfcCalendarRateController.resetServer();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TfcCalendarEventAcceleration.resetSession();
        TfcCalendarRateController.resetServer();
        TfcCalendarRateController.resetClient();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        sync(event.getEntity());
    }

    private static void sync(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            new TfcCalendarRateSyncPacket(TfcCalendarRateController.serverMultiplier()).sendTo(serverPlayer);
        }
    }
}
