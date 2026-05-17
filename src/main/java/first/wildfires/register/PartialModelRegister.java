package first.wildfires.register;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import first.wildfires.Wildfires;


public class PartialModelRegister {

    public static final PartialModel
            EmptySpool = path("block/loom/empty_spool"),
            Spool = path("block/loom/spool"),
            Fabric = path("block/loom/fabric"),

            StoneCogWheel = block("stone_cogwheel"),
            StoneLargeCogWheel = block("stone_large_cogwheel"),

    // 花岗岩
            GraniteCrushingWheel = block("granite_crushing_wheel"),
            GraniteMillingWheel = inner("granite_millstone"),

    // 闪长岩
            DioriteCrushingWheel = block("diorite_crushing_wheel"),
            DioriteMillingWheel = inner("diorite_millstone"),

    // 辉长岩
            GabbroCrushingWheel = block("gabbro_crushing_wheel"),
            GabbroMillingWheel = inner("gabbro_millstone"),

    // 流纹岩
            RhyoliteCrushingWheel = block("rhyolite_crushing_wheel"),
            RhyoliteMillingWheel = inner("rhyolite_millstone"),

    // 玄武岩
            BasaltCrushingWheel = block("basalt_crushing_wheel"),
            BasaltMillingWheel = inner("basalt_millstone"),

    // 英安岩
            DaciteCrushingWheel = block("dacite_crushing_wheel"),
            DaciteMillingWheel = inner("dacite_millstone");

    private static PartialModel path(String path) {
        return PartialModel.of(Wildfires.rl(path));
    }

    private static PartialModel block(String path) {
        return PartialModel.of(Wildfires.rl("block/" + path + "/block"));
    }

    private static PartialModel inner(String path) {
        return PartialModel.of(Wildfires.rl("block/" + path + "/inner"));
    }

    public static void register() {

    }

}
