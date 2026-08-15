/*
 * Adapted from VS: Genesis EnteringWarpPacket and transition-state hand-off.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236.
 * SPDX-License-Identifier: Apache-2.0
 * Wildfires changes: capsule-specific direction and ticket identity; no VS2 or wormhole types.
 */
package first.wildfires.network;

import first.wildfires.client.space.ReturnCapsuleClientTransition;
import first.wildfires.network.base.ICustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import first.wildfires.register.NetworkPacketRegister;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Supplier;

/** Arms the client before the vanilla dimension-receive screen can replace the flight scene. */
public record ReturnCapsuleTransitionPacket(UUID ticketId, UUID capsuleId,
                                            ResourceLocation targetDimension, boolean toStation)
        implements ICustomPacketPayload {

    public ReturnCapsuleTransitionPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUUID(), buffer.readResourceLocation(), buffer.readBoolean());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(ticketId);
        buffer.writeUUID(capsuleId);
        buffer.writeResourceLocation(targetDimension);
        buffer.writeBoolean(toStation);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ReturnCapsuleClientTransition.arm(
                ticketId, capsuleId, targetDimension, toStation));
        context.get().setPacketHandled(true);
    }

    public void sendTo(ServerPlayer player) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player), this);
    }
}
