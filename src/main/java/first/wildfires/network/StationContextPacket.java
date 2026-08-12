package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import first.wildfires.space.celestial.ObservationContext;
import first.wildfires.space.celestial.ObservationContextResolver;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Objects;
import java.util.function.Supplier;

/** Replaces the client's current server-authored station observation snapshot. */
public record StationContextPacket(ObservationContext observation) implements ICustomPacketPayload {

    public StationContextPacket {
        Objects.requireNonNull(observation, "observation");
    }

    public StationContextPacket(FriendlyByteBuf buffer) {
        this(SpacePacketCodecs.readContext(buffer));
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        SpacePacketCodecs.writeContext(buffer, observation);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ObservationContextResolver.acceptClient(observation));
    }

    public void sendTo(ServerPlayer player) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player), this);
    }
}
