package first.wildfires.space.content;

import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.station.SpaceSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.UUID;

/** Slotless menu carrying the immutable identity used by travel requests. */
public final class StationControlMenu extends AbstractContainerMenu {

    private final BlockPos computerPos;
    private final UUID stationId;
    private final long expectedRevision;

    public StationControlMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong());
    }

    public StationControlMenu(int containerId, Inventory inventory, BlockPos computerPos,
                              UUID stationId, long expectedRevision) {
        super(SpaceContentRegister.STATION_CONTROL_MENU.get(), containerId);
        Objects.requireNonNull(inventory, "inventory");
        this.computerPos = Objects.requireNonNull(computerPos, "computerPos").immutable();
        this.stationId = Objects.requireNonNull(stationId, "stationId");
        if (expectedRevision < 0L) {
            throw new IllegalArgumentException("Menu station revision must be non-negative");
        }
        this.expectedRevision = expectedRevision;
    }

    public BlockPos computerPos() {
        return computerPos;
    }

    public UUID stationId() {
        return stationId;
    }

    public long expectedRevision() {
        return expectedRevision;
    }

    public boolean matches(BlockPos pos, UUID expectedStation, long revision) {
        return computerPos.equals(pos) && stationId.equals(expectedStation)
                && expectedRevision == revision;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().dimension() != SpaceDimensions.ORBIT
                || player.distanceToSqr(computerPos.getX() + 0.5D, computerPos.getY() + 0.5D,
                computerPos.getZ() + 0.5D) > 64.0D
                || !player.level().getBlockState(computerPos)
                .is(SpaceContentRegister.STATION_CONTROL_COMPUTER.get())) {
            return false;
        }
        if (player.level().isClientSide()) {
            return true;
        }
        return SpaceSavedData.get(player.getServer()).stationAt(computerPos.getX(), computerPos.getZ())
                .filter(station -> station.stationId().equals(stationId))
                .filter(station -> station.region().containsBuildArea(computerPos))
                .filter(station -> station.mayOperate(player.getUUID())).isPresent();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
