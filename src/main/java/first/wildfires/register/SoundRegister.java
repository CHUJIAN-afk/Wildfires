package first.wildfires.register;

import first.wildfires.Wildfires;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class SoundRegister {

    private static final DeferredRegister<SoundEvent> Register = DeferredRegister.create(Registries.SOUND_EVENT, Wildfires.MODID);

    public static final RegistryObject<SoundEvent> MetalPipe = create("metal_pipe");
    public static final RegistryObject<SoundEvent> ReturnCapsuleIgnition = create("return_capsule_ignition");
    public static final RegistryObject<SoundEvent> ReturnCapsuleFlight = create("return_capsule_flight");

    private static RegistryObject<SoundEvent> create(String name) {
        return Register.register(name, () -> SoundEvent.createVariableRangeEvent(Wildfires.rl(name)));
    }

    public static void register(IEventBus bus) {
        Register.register(bus);
    }

}
