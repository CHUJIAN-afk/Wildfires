package first.wildfires.block;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlock;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import first.wildfires.register.BlockEntityRegister;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class CustomMillstoneBlock extends MillstoneBlock {

    private final PartialModel model;

    public CustomMillstoneBlock(PartialModel model, Properties properties) {
        super(properties);
        this.model = model;
    }

    public CustomMillstoneBlock(Properties properties) {
        this(null, properties);
    }

    public PartialModel getModel() {
        return model != null ? model : AllPartialModels.MILLSTONE_COG;
    }

    @Override
    public BlockEntityType<? extends MillstoneBlockEntity> getBlockEntityType() {
        return BlockEntityRegister.CustomMillstoneBlockEntity.get();
    }

}
