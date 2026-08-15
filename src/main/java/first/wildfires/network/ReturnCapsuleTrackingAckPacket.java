/*
 * Modern Forge companion to NTM: Space CelestialTeleporter's destination remount contract.
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

/** Confirms that the target client tracks the same capsule UUID and locally rides it. */
public record ReturnCapsuleTrackingAckPacket(UUID ticketId, UUID capsuleId)
        implements ICustomPacketPayload {

    public ReturnCapsuleTrackingAckPacket(FriendlyByteBuf buffer) {
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
        if (player != null) ReturnCapsuleService.confirmClientTracking(player, ticketId, capsuleId);
        context.get().setPacketHandled(true);
    }
}
