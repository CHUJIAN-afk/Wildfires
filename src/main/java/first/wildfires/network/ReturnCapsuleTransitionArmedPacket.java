/*
 * Modern Forge companion to NTM: Space CelestialTeleporter's rider-first transfer contract.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.space.capsule.ReturnCapsuleService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Proves that the source client captured and armed the exact transfer before Respawn is sent. */
public record ReturnCapsuleTransitionArmedPacket(UUID ticketId, UUID capsuleId)
        implements ICustomPacketPayload {

    public ReturnCapsuleTransitionArmedPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUUID());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(ticketId);
        buffer.writeUUID(capsuleId);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) ReturnCapsuleService.confirmClientArmed(player, ticketId, capsuleId);
        context.get().setPacketHandled(true);
    }
}
