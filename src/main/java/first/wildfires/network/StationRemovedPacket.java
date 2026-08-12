package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import first.wildfires.space.celestial.ObservationContextResolver;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Clears one station snapshot without granting the client any authority over server state. */
public record StationRemovedPacket(UUID stationId, long stationRevision) implements ICustomPacketPayload {

    public StationRemovedPacket {
        Objects.requireNonNull(stationId, "stationId");
        if (stationRevision < 0L) {
            throw new IllegalArgumentException("Removed station revision must be non-negative");
        }
    }

    public StationRemovedPacket(FriendlyByteBuf buffer) {
        this(SpacePacketCodecs.readRemoved(buffer));
    }

    private StationRemovedPacket(SpacePacketCodecs.Removed removed) {
        this(removed.stationId(), removed.revision());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        SpacePacketCodecs.writeRemoved(buffer, stationId, stationRevision);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() ->
                ObservationContextResolver.removeClient(stationId, stationRevision));
    }

    public void sendTo(ServerPlayer player) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player), this);
    }
}
