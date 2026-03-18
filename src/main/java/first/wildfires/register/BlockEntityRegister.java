package first.wildfires.register;

import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import first.wildfires.Wildfires;
import first.wildfires.client.renderer.blockEntity.CustomCogWheelBlockEntityRenderer;
import first.wildfires.client.renderer.blockEntity.CustomCrushingWheelBlockEntityRenderer;
import first.wildfires.client.renderer.blockEntity.CustomMillstoneBlockEntityRenderer;

import static first.wildfires.register.BlockRegister.*;


public class BlockEntityRegister {

    private static final CreateRegistrate Registrate = Wildfires.Registrate;

    public static final BlockEntityEntry<MillstoneBlockEntity> CustomMillstoneBlockEntity =
            Registrate.blockEntity("millstone", MillstoneBlockEntity::new)
                    .validBlocks(StoneMillstone)
                    .renderer(() -> CustomMillstoneBlockEntityRenderer::new)
                    .register();

    public static final BlockEntityEntry<CrushingWheelBlockEntity> CustomCrushingWheelBlockEntity =
            Registrate.blockEntity("crushing_wheel", CrushingWheelBlockEntity::new)
                    .validBlocks(StoneCrushingWheel)
                    .renderer(() -> CustomCrushingWheelBlockEntityRenderer::new)
                    .register();

    public static final BlockEntityEntry<BracketedKineticBlockEntity> CustomCogWheelBlockEntity =
            Registrate.blockEntity("cogwheel", BracketedKineticBlockEntity::new)
                    .validBlocks(StoneCogWheel, StoneLargeCogWheel)
                    .renderer(() -> CustomCogWheelBlockEntityRenderer::new)
                    .register();

    public static void register() {

    }

}
