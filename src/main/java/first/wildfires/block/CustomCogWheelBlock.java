package first.wildfires.block;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import first.wildfires.register.BlockEntityRegister;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class CustomCogWheelBlock extends CogWheelBlock {

    private final PartialModel model;
    private final boolean large;

    public CustomCogWheelBlock(PartialModel model, boolean large, Properties properties) {
        super(large, properties);
        this.model = model;
        this.large = large;
    }

    public PartialModel getModel() {
        return model;
    }

    public boolean isLarge() {
        return large;
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return BlockEntityRegister.CustomCogWheelBlockEntity.get();
    }

}
