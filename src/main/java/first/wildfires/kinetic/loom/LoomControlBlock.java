package first.wildfires.kinetic.loom;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import first.wildfires.register.BlockEntityRegister;
import first.wildfires.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class LoomControlBlock extends Block implements IBE<LoomControlBlockEntity>, IWrenchable {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public LoomControlBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        Direction right = facing.getClockWise();
        Direction back = facing.getOpposite();
        // 旋转轴：控制块朝向的垂直轴
        Axis rotationAxis = facing.getAxis() == Axis.X ? Axis.Z : Axis.X;

        // 结构块位置
        BlockPos rightPos = pos.relative(right);      // 右侧结构块，朝向控制块（左）
        BlockPos backPos1 = pos.relative(back);       // 正后方结构块，朝向控制块（前）
        BlockPos backPos2 = backPos1.relative(right); // 边角结构块，朝向正后方结构块（前）

        // 右侧结构块：朝向控制块（左方向）
        BlockState requiredRight = BlockRegister.LoomStructureBlock.getDefaultState()
                .setValue(LoomStructureBlock.AXIS, rotationAxis)
                .setValue(LoomStructureBlock.FACING, right.getOpposite()); // 朝向控制块

        // 正后方结构块：朝向控制块（前方向）
        BlockState requiredBack1 = BlockRegister.LoomStructureBlock.getDefaultState()
                .setValue(LoomStructureBlock.AXIS, rotationAxis)
                .setValue(LoomStructureBlock.FACING, back.getOpposite()); // 朝向控制块

        // 边角结构块：朝向正后方结构块（前方向）
        BlockState requiredBack2 = BlockRegister.LoomStructureBlock.getDefaultState()
                .setValue(LoomStructureBlock.AXIS, rotationAxis)
                .setValue(LoomStructureBlock.FACING, back.getOpposite()); // 朝向正后方结构块

        // 放置右侧结构块
        BlockState rightState = level.getBlockState(rightPos);
        if (rightState != requiredRight) {
            if (!rightState.canBeReplaced()) {
                level.destroyBlock(pos, true);
                return;
            }
            level.setBlockAndUpdate(rightPos, requiredRight);
        }

        // 放置正后方结构块
        BlockState backState1 = level.getBlockState(backPos1);
        if (backState1 != requiredBack1) {
            if (!backState1.canBeReplaced()) {
                level.destroyBlock(pos, true);
                return;
            }
            level.setBlockAndUpdate(backPos1, requiredBack1);
        }

        // 放置边角结构块
        BlockState backState2 = level.getBlockState(backPos2);
        if (backState2 != requiredBack2) {
            if (!backState2.canBeReplaced()) {
                level.destroyBlock(pos, true);
                return;
            }
            level.setBlockAndUpdate(backPos2, requiredBack2);
        }
    }

    // 控制块移除时不主动清理结构块，结构块会通过stillValid检查自行销毁

    @Override
    public Class<LoomControlBlockEntity> getBlockEntityClass() {
        return LoomControlBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LoomControlBlockEntity> getBlockEntityType() {
        return BlockEntityRegister.LoomControlBlockEntity.get();
    }
}