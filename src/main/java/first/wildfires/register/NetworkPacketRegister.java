package first.wildfires.register;

import first.wildfires.Wildfires;
import first.wildfires.network.PlayerInputPacket;
import first.wildfires.network.TemperatureFilterSyncPacket;
import first.wildfires.network.ThermalOffsetSyncPacket;
import first.wildfires.network.ThermalDebugRequestPacket;
import first.wildfires.network.ThermalDebugSnapshotPacket;
import first.wildfires.network.CelestialSettingsSyncPacket;
import first.wildfires.network.TfcCalendarRateSyncPacket;
import first.wildfires.network.StationContextPacket;
import first.wildfires.network.StationRemovedPacket;
import first.wildfires.network.RequestStationTravelPacket;
import first.wildfires.network.base.ICustomPacketPayload;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkPacketRegister {

    public static final String Version = "8";
    public static final SimpleChannel Instance = NetworkRegistry.newSimpleChannel(Wildfires.rl("main"), () -> Version, Version::equals, Version::equals);
    public static int id = 1;

    public static void register() {
        ICustomPacketPayload.register(PlayerInputPacket.class, NetworkDirection.PLAY_TO_SERVER);
        ICustomPacketPayload.register(TemperatureFilterSyncPacket.class, NetworkDirection.PLAY_TO_SERVER);
        ICustomPacketPayload.register(ThermalOffsetSyncPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(ThermalDebugRequestPacket.class, NetworkDirection.PLAY_TO_SERVER);
        ICustomPacketPayload.register(ThermalDebugSnapshotPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(CelestialSettingsSyncPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(TfcCalendarRateSyncPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(StationContextPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(StationRemovedPacket.class, NetworkDirection.PLAY_TO_CLIENT);
        ICustomPacketPayload.register(RequestStationTravelPacket.class, NetworkDirection.PLAY_TO_SERVER);
    }

}
