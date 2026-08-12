package first.wildfires.space.content;

import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.station.SpaceSavedData;
import first.wildfires.space.station.StationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/** The only first-release target-selection entry point. */
public final class StationControlComputerBlock extends BaseEntityBlock {

    public StationControlComputerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationControlComputerBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || serverLevel.dimension() != SpaceDimensions.ORBIT) {
            player.displayClientMessage(Component.translatable(
                    "space.wildfires.computer.orbit_only"), true);
            return InteractionResult.CONSUME;
        }
        StationRecord station = SpaceSavedData.get(serverLevel.getServer())
                .stationAt(pos.getX(), pos.getZ()).orElse(null);
        if (station == null || !station.region().containsBuildArea(pos)) {
            player.displayClientMessage(Component.translatable(
                    "space.wildfires.computer.outside_station"), true);
            return InteractionResult.CONSUME;
        }
        if (!station.mayOperate(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                    "space.wildfires.travel.permission_denied"), true);
            return InteractionResult.CONSUME;
        }
        if (!(level.getBlockEntity(pos) instanceof StationControlComputerBlockEntity computer)) {
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(serverPlayer, computer, buffer -> {
            buffer.writeBlockPos(pos);
            buffer.writeUUID(station.stationId());
            buffer.writeVarLong(station.revision());
        });
        return InteractionResult.CONSUME;
    }
}
