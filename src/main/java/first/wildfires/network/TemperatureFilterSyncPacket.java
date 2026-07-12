package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Syncs temperature filter list from client to server.
 * The server writes the filters into the AttributeFilter item's NBT directly.
 */
public record TemperatureFilterSyncPacket(ListTag filters) implements ICustomPacketPayload {

    public static final String TEMPERATURE_FILTERS_KEY = "TemperatureFilters";

    public TemperatureFilterSyncPacket(FriendlyByteBuf buffer) {
        this(readFilters(buffer));
    }

    private static ListTag readFilters(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        if (tag == null) return new ListTag();
        return tag.getList(TEMPERATURE_FILTERS_KEY, net.minecraft.nbt.Tag.TAG_STRING);
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        CompoundTag tag = new CompoundTag();
        tag.put(TEMPERATURE_FILTERS_KEY, filters);
        buffer.writeNbt(tag);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null) return;

            // The player's main hand item IS the filter item (contentHolder)
            ItemStack filterStack = player.getMainHandItem();
            if (filterStack.isEmpty()) return;

            if (filters.isEmpty()) {
                CompoundTag existingTag = filterStack.getTag();
                if (existingTag != null) {
                    existingTag.remove(TEMPERATURE_FILTERS_KEY);
                }
            } else {
                filterStack.getOrCreateTag().put(TEMPERATURE_FILTERS_KEY, filters);
            }
        });
    }

    /**
     * Build a ListTag from a list of filter strings for sending.
     */
    public static TemperatureFilterSyncPacket fromFilterList(java.util.List<String> filterList) {
        ListTag listTag = new ListTag();
        for (String filter : filterList) {
            listTag.add(StringTag.valueOf(filter));
        }
        return new TemperatureFilterSyncPacket(listTag);
    }
}
