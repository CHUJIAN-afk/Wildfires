package first.wildfires.network.base;

import first.wildfires.register.NetworkPacketRegister;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 自定义网络包接口
 * 所有自定义网络包都需要实现此接口
 * 并在NetworkPacketRegister中注册
 * 网络包的编码和解码逻辑需要在实现类中完成
 * 必须有一个构造函数，参数为FriendlyByteBuf，用于解码网络包
 */
public interface ICustomPacketPayload {

    void encode(FriendlyByteBuf buffer);

    void handle(Supplier<NetworkEvent.Context> context);

    default void sendToServer() {
        NetworkPacketRegister.Instance.sendToServer(this);
    }

    default void sendToAllClients() {
        NetworkPacketRegister.Instance.send(PacketDistributor.ALL.noArg(), this);
    }

    static <T extends ICustomPacketPayload> void register(Class<T> packetClass) {
        NetworkPacketRegister.Instance.messageBuilder(packetClass, NetworkPacketRegister.id++)
                .encoder(T::encode)
                .decoder(buf -> {
                    try {
                        return packetClass.getConstructor(FriendlyByteBuf.class).newInstance(buf);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .consumerMainThread(ICustomPacketPayload::handle)
                .add();
    }

}
