package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public record PlayerInputPacket() implements ICustomPacketPayload {

    public PlayerInputPacket(FriendlyByteBuf buffer) {
        this();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {

    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                Entity vehicle = player.getVehicle();
                if (vehicle != null) {
                    ResourceLocation location = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
                    if (location != null) {
                        String string = location.toString();
                        if (string.equals("alexscaves:submarine")) {
                            Vec3 vec3 = player.getDeltaMovement();
                            if (vec3.y() < 0.1) {
                                player.addDeltaMovement(new Vec3(0, 0.1, 0));
                            }
                        }
                    }
                }
            }
        });
    }

}
