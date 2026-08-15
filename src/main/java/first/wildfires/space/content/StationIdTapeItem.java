/*
 * Adapted from NTM: Space ItemVOTVdrive orbital-station destination contract.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: replaced mutable grid coordinates and client-cached target fields with
 * an authoritative station UUID; programming occurs only against a bound station core and every
 * launch resolves the UUID against current server SavedData and permissions.
 */
package first.wildfires.space.content;

import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A one-station navigation tape for selecting the return capsule's orbital destination. */
public final class StationIdTapeItem extends Item {

    private static final String DATA_VERSION = "wildfires_station_tape_version";
    private static final String STATION_ID = "wildfires_station_id";
    private static final String STATION_NAME = "wildfires_station_name";
    private static final int CURRENT_VERSION = 1;

    public StationIdTapeItem(Properties properties) {
        super(properties);
    }

    public static InteractionResult programFromCore(Level level, BlockPos pos, Player player,
                                                     InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.dimension() != SpaceDimensions.ORBIT
                || !(level.getBlockEntity(pos) instanceof StationCoreBlockEntity core)) {
            return InteractionResult.FAIL;
        }
        StationRecord station = core.stationId().flatMap(id -> SpaceSavedData.get(level.getServer())
                .station(id)).orElse(null);
        if (station == null || !station.mayOperate(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                    "space.wildfires.station_id_tape.denied"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack tape = player.getItemInHand(hand);
        write(tape, station);
        player.displayClientMessage(Component.translatable(
                "space.wildfires.station_id_tape.programmed", station.name(), station.stationId()), true);
        return InteractionResult.CONSUME;
    }

    public static Optional<UUID> stationId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.getInt(DATA_VERSION) != CURRENT_VERSION
                || !tag.contains(STATION_ID, Tag.TAG_INT_ARRAY)
                || tag.getIntArray(STATION_ID).length != 4) return Optional.empty();
        return Optional.of(tag.getUUID(STATION_ID));
    }

    /** Creates one canonical programmed tape for commands and other trusted server-side entry points. */
    public static ItemStack createProgrammed(StationRecord station) {
        ItemStack stack = new ItemStack(SpaceContentRegister.STATION_ID_TAPE.get());
        write(stack, station);
        return stack;
    }

    /**
     * Migration-only writer for capsules saved before the navigation tape became a real internal
     * slot. Runtime programming must still resolve a live {@link StationRecord}.
     */
    public static ItemStack createMigrated(UUID stationId) {
        ItemStack stack = new ItemStack(SpaceContentRegister.STATION_ID_TAPE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(DATA_VERSION, CURRENT_VERSION);
        tag.putUUID(STATION_ID, stationId);
        tag.putString(STATION_NAME, stationId.toString());
        return stack;
    }

    private static void write(ItemStack stack, StationRecord station) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(DATA_VERSION, CURRENT_VERSION);
        tag.putUUID(STATION_ID, station.stationId());
        tag.putString(STATION_NAME, station.name());
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && stationId(stack).isPresent()) {
            return Component.translatable("item.wildfires.station_id_tape.programmed",
                    tag.getString(STATION_NAME));
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines,
                                TooltipFlag flag) {
        MutableComponent line = stationId(stack)
                .<MutableComponent>map(id -> Component.translatable(
                        "item.wildfires.station_id_tape.station", id))
                .orElseGet(() -> Component.translatable("item.wildfires.station_id_tape.blank"));
        lines.add(line.withStyle(stationId(stack).isPresent() ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }
}
