package first.wildfires.kinetic.loom;

import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import first.wildfires.Wildfires;
import first.wildfires.register.BlockEntityRegister;
import first.wildfires.register.BlockRegister;
import first.wildfires.utils.VoxelShapeParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;

public class LoomStructureBlock extends RotatedPillarKineticBlock implements IBE<LoomStructureBlockEntity>, IProxyHoveringInformation {

    // 结构块自身的朝向，指向它应该连接的方向（控制块或前方的结构块）
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;


    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        Block block = pLevel.getBlockState(pPos.relative(pState.getValue(FACING))).getBlock();
        if (block instanceof LoomAuxiliaryBlock) {
            return VoxelShapeParser.getOrParse(Wildfires.rl("models/block/loom0"));
        }
        if (block instanceof LoomControlBlock) {
            return VoxelShapeParser.getOrParse(Wildfires.rl("models/block/loom1"));
        }
        return super.getShape(pState, pLevel, pPos, pContext);
    }

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
        if (targetState.getBlock() instanceof LoomControlBlock) {
            return targetPos;
        }

        // 如果前方是另一个结构块，继续递归
        if (targetState.getBlock() instanceof LoomStructureBlock || targetState.getBlock() instanceof LoomAuxiliaryBlock) {
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

    @Override
    public BlockPos getInformationSource(Level level, BlockPos blockPos, BlockState blockState) {
        return findMasterRecursive(level, blockPos, blockState);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // 找到控制块
        BlockPos masterPos = findMasterRecursive(level, pos, state);
        if (masterPos == null) {
            return InteractionResult.FAIL;
        }

        if (level.getBlockEntity(masterPos) instanceof LoomControlBlockEntity controlBE) {
            IItemHandler handler = controlBE.getItemHandlerLazy().resolve().orElse(null);
            if (handler == null) {
                return InteractionResult.FAIL;
            }

            ItemStack heldItem = player.getItemInHand(hand);

            if (!heldItem.isEmpty()) {
                // 手上有物品，尝试插入
                return insertItemToHandler(level, player, hand, handler, heldItem);
            } else {
                // 手上无物品，尝试取出
                return extractItemFromHandler(level, player, hand, handler);
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * 将玩家手中的物品插入到物品处理器
     */
    private InteractionResult insertItemToHandler(Level level, Player player, InteractionHand hand, IItemHandler handler, ItemStack heldItem) {
        // 尝试插入物品
        ItemStack remaining = ItemHandlerHelper.insertItem(handler, heldItem, false);

        if (remaining.getCount() < heldItem.getCount()) {
            // 成功插入部分或全部
            if (remaining.isEmpty()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            } else {
                player.setItemInHand(hand, remaining);
            }

            // 挥手动画
            player.swing(hand);

            // 播放音效
            if (!level.isClientSide) {
                level.levelEvent(1000, player.blockPosition(), 0); // 物品拾取音效
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    /**
     * 从物品处理器取出物品给玩家
     */
    private InteractionResult extractItemFromHandler(Level level, Player player, InteractionHand hand, IItemHandler handler) {
        // 从最后一个槽位开始查找非空物品
        for (int i = handler.getSlots() - 1; i >= 0; i--) {
            ItemStack extracted = handler.extractItem(i, 64, false);
            if (!extracted.isEmpty()) {
                // 成功取出物品
                player.setItemInHand(hand, extracted);

                // 挥手动画
                player.swing(hand);

                // 播放音效
                if (!level.isClientSide) {
                    level.levelEvent(1000, player.blockPosition(), 0); // 物品拾取音效
                }

                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return InteractionResult.PASS;
    }
}