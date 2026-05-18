package first.wildfires.kinetic.loom;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import first.wildfires.Wildfires;
import first.wildfires.register.BlockEntityRegister;
import first.wildfires.register.BlockRegister;
import first.wildfires.utils.VoxelShapeParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class LoomControlBlock extends Block implements IBE<LoomControlBlockEntity>, IWrenchable{

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public LoomControlBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        Direction facing = pState.getValue(FACING);
        // 旋转180度
        Direction rotation = facing.getClockWise().getClockWise();
        double v = 0.0625;
        double offsetX = switch (facing) {
            case EAST -> -v;
            case WEST -> v;
            default -> 0;
        };
        double offsetZ = switch (facing) {
            case NORTH -> v;
            case SOUTH -> -v;
            default -> 0;
        };
        return VoxelShapeParser.getOrParseTransformed(Wildfires.rl("block/loom_hitbox4"), rotation, offsetX, 0, offsetZ);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public List<ItemStack> getDrops(BlockState pState, LootParams.Builder pParams) {
        List<ItemStack> itemStacks = super.getDrops(pState, pParams);
        itemStacks.add(BlockRegister.LoomControlBlock.asItem().getDefaultInstance());
        return itemStacks;
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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // 掉落储存物品
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof LoomControlBlockEntity loomBE) {
                loomBE.dropInventory();
            }

            // 同步摧毁其他三个结构块
            Direction facing = state.getValue(FACING);
            Direction right = facing.getClockWise();
            Direction back = facing.getOpposite();

            BlockPos rightPos = pos.relative(right);
            BlockPos backPos1 = pos.relative(back);
            BlockPos backPos2 = backPos1.relative(right);

            // 摧毁右侧辅助块
            if (level.getBlockState(rightPos).getBlock() instanceof LoomAuxiliaryBlock) {
                level.destroyBlock(rightPos, false);
            }
            // 摧毁正后方结构块
            if (level.getBlockState(backPos1).getBlock() instanceof LoomStructureBlock) {
                level.destroyBlock(backPos1, false);
            }
            // 摧毁边角结构块
            if (level.getBlockState(backPos2).getBlock() instanceof LoomStructureBlock) {
                level.destroyBlock(backPos2, false);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // 播放放置音效
        level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
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

        // 右侧辅助块：朝向控制块（左方向），使用辅助块而非普通结构块
        BlockState requiredRight = BlockRegister.LoomAuxiliaryBlock.getDefaultState()
                .setValue(LoomAuxiliaryBlock.FACING, right.getOpposite()); // 朝向控制块

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

    @Override
    public Class<LoomControlBlockEntity> getBlockEntityClass() {
        return LoomControlBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LoomControlBlockEntity> getBlockEntityType() {
        return BlockEntityRegister.LoomControlBlockEntity.get();
    }
}