package first.wildfires.network;

import first.wildfires.compats.legendarysurvivaloverhaul.TemperatureRangeManager;
import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Synchronizes the server-authoritative five LSO temperature bands to one client. */
public record TemperatureRangesSyncPacket(int[] bounds) implements ICustomPacketPayload {

    public TemperatureRangesSyncPacket {
        if (bounds == null || bounds.length != TemperatureRangeManager.BOUND_COUNT) {
            throw new IllegalArgumentException("Expected " + TemperatureRangeManager.BOUND_COUNT
                    + " LSO temperature bounds");
        }
        bounds = bounds.clone();
    }

    public TemperatureRangesSyncPacket(FriendlyByteBuf buffer) {
        this(readBounds(buffer));
    }

    @Override
    public int[] bounds() {
        return bounds.clone();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        for (int bound : bounds) {
            buffer.writeInt(bound);
        }
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> TemperatureRangeManager.applySynchronizedBounds(bounds));
    }

    public static void send(ServerPlayer player) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player),
                new TemperatureRangesSyncPacket(TemperatureRangeManager.snapshot()));
    }

    private static int[] readBounds(FriendlyByteBuf buffer) {
        int[] bounds = new int[TemperatureRangeManager.BOUND_COUNT];
        for (int index = 0; index < bounds.length; index++) {
            bounds[index] = buffer.readInt();
        }
        return bounds;
    }
}
