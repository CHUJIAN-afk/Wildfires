package first.wildfires.register;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import first.wildfires.Wildfires;

public class PartialModelRegister {

    public static final PartialModel
            StoneCogWheel = block("stone_cogwheel"),
            StoneLargeCogWheel = block("stone_large_cogwheel");

    private static PartialModel block(String path) {
        return PartialModel.of(Wildfires.rl("block/" + path));
    }

    public static void register() {

    }

}
