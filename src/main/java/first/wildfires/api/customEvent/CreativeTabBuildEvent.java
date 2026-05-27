package first.wildfires.api.customEvent;

import first.wildfires.register.CreativeModeTabRegister;
import net.minecraftforge.eventbus.api.Event;

public class CreativeTabBuildEvent extends Event {

    private final CreativeModeTabRegister.Registers registers;

    public CreativeTabBuildEvent(CreativeModeTabRegister.Registers registers) {
        this.registers = registers;
    }

    public CreativeModeTabRegister.Registers getRegisters() {
        return registers;
    }
}
