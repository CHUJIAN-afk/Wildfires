package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.thermal.ClientThermalState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sends the server-calculated local thermal offset to one client. */
public record ThermalOffsetSyncPacket(float offset) implements ICustomPacketPayload {

    public ThermalOffsetSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readFloat());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(offset);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientThermalState.setLocalOffset(offset));
    }
}
