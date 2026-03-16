package first.wildfires.compats.kubejs.spartan.builders;

import com.oblivioussp.spartanweaponry.util.WeaponFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import first.wildfires.compats.kubejs.spartan.SpartanWeaponBuilder;

public class HeavyCrossbowBuilder extends SpartanWeaponBuilder {

    public HeavyCrossbowBuilder(ResourceLocation i) {
        super(i);
    }

    @Override
    public Item createObject() {
        var item = WeaponFactory.HEAVY_CROSSBOW.create(material, new Item.Properties());

        setDuration(item, duration);

        return item;
    }
}
