package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.thermal.ClientThermalState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Synchronizes local air, radiation, and combined thermal values to one client. */
public record ThermalOffsetSyncPacket(float airTemperature, float radiationOffset,
                                      float effectiveTemperature) implements ICustomPacketPayload {

    public ThermalOffsetSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(airTemperature);
        buffer.writeFloat(radiationOffset);
        buffer.writeFloat(effectiveTemperature);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() ->
                ClientThermalState.setTargets(airTemperature, radiationOffset, effectiveTemperature));
    }
}
