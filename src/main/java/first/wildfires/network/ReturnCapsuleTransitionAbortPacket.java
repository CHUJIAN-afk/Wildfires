/*
 * Modern Forge recovery companion to NTM: Space CelestialTeleporter's destination remount
 * contract.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package first.wildfires.network;

import first.wildfires.client.space.ReturnCapsuleClientTransition;
import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/** Cancels only the exact client transition whose server transaction entered recovery. */
public record ReturnCapsuleTransitionAbortPacket(UUID ticketId, UUID capsuleId)
        implements ICustomPacketPayload {

    public ReturnCapsuleTransitionAbortPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUUID());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(ticketId);
        buffer.writeUUID(capsuleId);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ReturnCapsuleClientTransition.abort(ticketId, capsuleId));
        context.get().setPacketHandled(true);
    }

    public void sendTo(ServerPlayer player) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player), this);
    }
}
