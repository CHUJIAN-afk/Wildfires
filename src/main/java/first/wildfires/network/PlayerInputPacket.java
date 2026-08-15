package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import first.wildfires.space.capsule.ReusableReturnCapsuleEntity;
import first.wildfires.space.capsule.ReturnCapsuleService;

import java.util.function.Supplier;

public record PlayerInputPacket(boolean pressed) implements ICustomPacketPayload {

    public PlayerInputPacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(pressed);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                Entity vehicle = player.getVehicle();
                if (vehicle != null) {
                    if (vehicle instanceof ReusableReturnCapsuleEntity capsule) {
                        ReturnCapsuleService.handlePrimaryActionInput(player, capsule, pressed)
                                .filter(result -> !result.successful())
                                .ifPresent(result -> player.displayClientMessage(
                                        Component.translatable(result.translationKey()), true));
                        return;
                    }
                    ReturnCapsuleService.recordPrimaryActionInput(player, pressed);
                    if (!pressed) return;
                    ResourceLocation location = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
                    if (location != null) {
                        String string = location.toString();
                        if (string.equals("alexscaves:submarine")) {
                            Vec3 vec3 = player.getDeltaMovement();
                            if (vec3.y() < 0.1) {
                                vehicle.addDeltaMovement(new Vec3(0, 0.05, 0));
                            }
                        }
                    }
                } else {
                    ReturnCapsuleService.recordPrimaryActionInput(player, pressed);
                }
            }
        });
    }

}
