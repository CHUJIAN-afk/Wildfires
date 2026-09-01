package first.wildfires.network;

/*
 * Visual trigger values adapted from ArcaneVortex 0.6.8 Sky Ripper and
 * ScreenShakeHelper under the user's project-specific visual authorization.
 * This packet carries no damage or attack decision.
 */
import first.wildfires.client.spell.GalaxyHymnImpactVisuals;
import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Sends the impact center, visual seed and already distance-scaled shake intensity to one observing client. */
public record GalaxyHymnImpactVisualPacket(Vec3 center, float shakeIntensity, int visualSeed,
                                            boolean completeBurst)
        implements ICustomPacketPayload {

    public static final double SHAKE_RADIUS = 60.0D;
    public static final float BASE_SHAKE_INTENSITY = 5.0F;

    public GalaxyHymnImpactVisualPacket(FriendlyByteBuf buffer) {
        this(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readFloat(),
                buffer.readInt(), buffer.readBoolean());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeDouble(center.x);
        buffer.writeDouble(center.y);
        buffer.writeDouble(center.z);
        buffer.writeFloat(shakeIntensity);
        buffer.writeInt(visualSeed);
        buffer.writeBoolean(completeBurst);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> GalaxyHymnImpactVisuals.trigger(
                center, shakeIntensity, visualSeed, completeBurst));
        context.get().setPacketHandled(true);
    }

    public void sendTo(ServerPlayer player) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player), this);
    }
}
