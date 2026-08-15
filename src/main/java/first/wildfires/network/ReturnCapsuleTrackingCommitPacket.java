/*
 * Modern Forge companion to NTM: Space CelestialTeleporter's destination remount contract.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package first.wildfires.network;

import first.wildfires.client.space.ReturnCapsuleClientTransition;
import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/** Confirms that the server accepted the client's exact target passenger graph. */
public record ReturnCapsuleTrackingCommitPacket(UUID ticketId, UUID capsuleId,
                                                ResourceLocation targetDimension)
        implements ICustomPacketPayload {

    public ReturnCapsuleTrackingCommitPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUUID(), buffer.readResourceLocation());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(ticketId);
        buffer.writeUUID(capsuleId);
        buffer.writeResourceLocation(targetDimension);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ReturnCapsuleClientTransition.confirm(
                ticketId, capsuleId, targetDimension));
        context.get().setPacketHandled(true);
    }

    public void sendTo(ServerPlayer player) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player), this);
    }
}
