package first.wildfires.block;

import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import first.wildfires.register.BlockEntityRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CustomCrushingWheelBlock extends CrushingWheelBlock {

    private final PartialModel model;

    public CustomCrushingWheelBlock(PartialModel model, Properties properties) {
        super(properties);
        this.model = model;
    }

    @Override
    public void updateControllers(BlockState state, Level world, BlockPos pos, Direction side) {
        super.updateControllers(state, world, pos, side);
    }

    public PartialModel getModel() {
        return model;
    }

    @Override
    public BlockEntityType<? extends CrushingWheelBlockEntity> getBlockEntityType() {
        return BlockEntityRegister.CustomCrushingWheelBlockEntity.get();
    }

}
