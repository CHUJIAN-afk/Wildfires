package first.wildfires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A non-functional decorative block with the same shape as TFC's crucible. */
public class DecorativeCrucibleBlock extends Block {

    private static final VoxelShape SHAPE = Shapes.or(
            box(3, 0, 3, 13, 2, 13),
            box(1, 1, 1, 15, 16, 3),
            box(1, 1, 13, 15, 16, 15),
            box(1, 1, 1, 3, 16, 15),
            box(13, 1, 1, 15, 16, 15)
    );

    private static final VoxelShape INTERACTION_SHAPE = Shapes.or(
            box(3, 0, 3, 13, 2, 13),
            box(1, 1, 1, 15, 16, 15)
    );

    public DecorativeCrucibleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return INTERACTION_SHAPE;
    }
}
