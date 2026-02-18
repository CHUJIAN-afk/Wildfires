package first.wildfires.register;

import first.wildfires.Wildfires;
import first.wildfires.network.PlayerInputPacket;
import first.wildfires.network.base.ICustomPacketPayload;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkPacketRegister {

    public static final String Version = "1";
    public static final SimpleChannel Instance = NetworkRegistry.newSimpleChannel(Wildfires.rl("main"), () -> Version, Version::equals, Version::equals);
    public static int id = 1;

    public static void register() {
        ICustomPacketPayload.register(PlayerInputPacket.class);
    }

}
