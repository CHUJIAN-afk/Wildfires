package first.wildfires.space.content;

import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Menu provider; authoritative station binding is resolved from its position each time. */
public final class StationControlComputerBlockEntity extends BlockEntity implements MenuProvider {

    public StationControlComputerBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContentRegister.STATION_CONTROL_COMPUTER_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.wildfires.station_control.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.dimension() != SpaceDimensions.ORBIT) {
            return null;
        }
        StationRecord station = SpaceSavedData.get(serverLevel.getServer())
                .stationAt(worldPosition.getX(), worldPosition.getZ()).orElse(null);
        if (station == null || !station.region().containsBuildArea(worldPosition)
                || !station.mayOperate(player.getUUID())) {
            return null;
        }
        return new StationControlMenu(containerId, inventory, worldPosition,
                station.stationId(), station.revision());
    }
}
