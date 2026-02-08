package first.wildfires.register;

import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import first.wildfires.Wildfires;
import first.wildfires.client.CustomCogWheelBlockEntityRenderer;

import static first.wildfires.register.BlockRegister.*;


public class BlockEntityRegister {

    private static final CreateRegistrate Registrate = Wildfires.Registrate;

    public static final BlockEntityEntry<BracketedKineticBlockEntity> CustomCogWheelBlockEntity =
            Registrate.blockEntity("cogwheel", BracketedKineticBlockEntity::new)
                    .validBlocks(StoneCogWheel, StoneLargeCogWheel)
                    .renderer(() -> CustomCogWheelBlockEntityRenderer::new)
                    .register();

    public static void register() {

    }

}
