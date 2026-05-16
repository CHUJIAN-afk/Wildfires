package first.wildfires.kinetic.loom;

import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import first.wildfires.register.BlockEntityRegister;
import first.wildfires.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.NotNull;

public class LoomAuxiliaryBlock extends Block implements IBE<LoomAuxiliaryBlockEntity>, IWrenchable , IProxyHoveringInformation {

    // 辅助块自身的朝向，指向控制块
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public LoomAuxiliaryBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return BlockRegister.LoomControlBlock.asStack();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // 辅助块被移除时，找到控制块并销毁它
        if (!state.is(newState.getBlock())) {
            BlockPos masterPos = findMaster(level, pos, state);
            if (masterPos != null) {
                BlockState masterState = level.getBlockState(masterPos);
                if (masterState.is(BlockRegister.LoomControlBlock.get())) {
                    level.destroyBlock(masterPos, true);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @NotNull BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        // 检查是否仍然有效
        if (!stillValid(level, currentPos, state)) {
            if (level instanceof Level l && !l.isClientSide() && !l.getBlockTicks().hasScheduledTick(currentPos, this))
                l.scheduleTick(currentPos, this, 1);
        }
        return state;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 如果不再有效，自行销毁（这会触发onRemove，进而销毁控制块）
        if (!stillValid(level, pos, state))
            level.destroyBlock(pos, false);
    }

    /**
     * 查找控制块（辅助块直接指向控制块）
     */
    public static BlockPos findMaster(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos targetPos = pos.relative(facing);
        BlockState targetState = level.getBlockState(targetPos);

        // 如果前方是控制块，返回其位置
        if (targetState.getBlock() instanceof LoomControlBlock) {
            return targetPos;
        }

        return null;
    }

    /**
     * 检查辅助块是否仍然有效（能否找到控制块）
     */
    public boolean stillValid(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.is(this)) return false;
        BlockPos masterPos = findMaster(level, pos, state);
        return masterPos != null;
    }

    @Override
    public Class<LoomAuxiliaryBlockEntity> getBlockEntityClass() {
        return LoomAuxiliaryBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LoomAuxiliaryBlockEntity> getBlockEntityType() {
        return BlockEntityRegister.LoomAuxiliaryBlockEntity.get();
    }

    @Override
    public BlockPos getInformationSource(Level level, BlockPos blockPos, BlockState blockState) {
        return findMaster(level, blockPos, blockState);
    }
}