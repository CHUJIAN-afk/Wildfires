package first.wildfires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class CustomDirectionalBlock extends DirectionalBlock {

    public static final DirectionProperty Facing =  BlockStateProperties.HORIZONTAL_FACING;

    private final Map<Direction, VoxelShape> shapes;

    public CustomDirectionalBlock(Properties properties, VoxelShape north, VoxelShape east, VoxelShape south, VoxelShape west) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(Facing, Direction.NORTH));
        shapes = new HashMap<>();
        shapes.put(Direction.NORTH, north);
        shapes.put(Direction.EAST, east);
        shapes.put(Direction.SOUTH, south);
        shapes.put(Direction.WEST, west);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Facing);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockState blockState = super.getStateForPlacement(context);
        if (blockState != null && blockState.hasProperty(Facing)) {
            return blockState.setValue(Facing, context.getHorizontalDirection().getOpposite());
        }
        return super.getStateForPlacement(context);
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull VoxelShape getShape(BlockState state, @NotNull BlockGetter pLevel, @NotNull BlockPos pPos, @NotNull CollisionContext pContext) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            Direction direction = state.getValue(BlockStateProperties.FACING);
            return shapes.get(direction);
        }
        return super.getShape(state, pLevel, pPos, pContext);
    }

}
