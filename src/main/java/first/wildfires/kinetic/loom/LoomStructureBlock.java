package first.wildfires.kinetic.loom;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
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

public class LoomStructureBlock extends RotatedPillarKineticBlock implements IBE<LoomStructureBlockEntity>, IWrenchable {

    // 结构块自身的朝向，指向它应该连接的方向（控制块或前方的结构块）
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public LoomStructureBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Axis.Y).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return BlockRegister.LoomControlBlock.asStack();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // 结构块被移除时，找到控制块并销毁它
        if (!state.is(newState.getBlock())) {
            BlockPos masterPos = findMasterRecursive(level, pos, state);
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
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        // 找到控制块并显示破坏进度
        BlockPos masterPos = findMasterRecursive(level, pos, state);
        if (masterPos != null && !masterPos.equals(pos)) {
            level.destroyBlockProgress(masterPos.hashCode(), masterPos, -1);
        }
        super.playerWillDestroy(level, pos, state, player);
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
     * 递归查找控制块
     * 结构块通过FACING指向它应该连接的方向
     */
    public static BlockPos findMasterRecursive(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos targetPos = pos.relative(facing);
        BlockState targetState = level.getBlockState(targetPos);

        // 如果前方是控制块，返回其位置
        if (targetState.is(BlockRegister.LoomControlBlock.get())) {
            return targetPos;
        }

        // 如果前方是另一个结构块，继续递归
        if (targetState.is(BlockRegister.LoomStructureBlock.get())) {
            return findMasterRecursive(level, targetPos, targetState);
        }

        // 找不到有效的连接
        return null;
    }

    /**
     * 检查结构块是否仍然有效（能否通过递归找到控制块）
     */
    public boolean stillValid(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.is(this)) return false;
        BlockPos masterPos = findMasterRecursive(level, pos, state);
        return masterPos != null;
    }

    @Override
    public Class<LoomStructureBlockEntity> getBlockEntityClass() {
        return LoomStructureBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LoomStructureBlockEntity> getBlockEntityType() {
        return BlockEntityRegister.LoomStructureBlockEntity.get();
    }
}