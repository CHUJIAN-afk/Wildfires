package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import first.wildfires.tfc.calendar.CalendarRateAccumulator;
import first.wildfires.tfc.calendar.TfcCalendarRateController;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Synchronizes the authoritative TFC calendar multiplier used for smooth client interpolation. */
public record TfcCalendarRateSyncPacket(double multiplier) implements ICustomPacketPayload {

    public TfcCalendarRateSyncPacket {
        CalendarRateAccumulator.validateMultiplier(multiplier);
    }

    public TfcCalendarRateSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readDouble());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(multiplier);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> TfcCalendarRateController.acceptClientMultiplier(multiplier));
    }

    public void sendTo(ServerPlayer player) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player), this);
    }

    public void sendToAll() {
        NetworkPacketRegister.Instance.send(PacketDistributor.ALL.noArg(), this);
    }
}
