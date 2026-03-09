package first.wildfires.register;

import first.wildfires.Wildfires;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class AttributeRegister {

    private static final DeferredRegister<Attribute> Register = DeferredRegister.create(Registries.ATTRIBUTE, Wildfires.MODID);

    public static final RegistryObject<Attribute> ArmorPenetration = Register.register("armor_penetration", () -> new RangedAttribute(Wildfires.rl("armor_penetration").toString(), 0, 0, 1000));

    public static void register(IEventBus eventBus) {
        Register.register(eventBus);
    }

}
