package first.wildfires.compats.kubejs.spartan.builders;

import com.oblivioussp.spartanweaponry.util.WeaponFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import first.wildfires.compats.kubejs.spartan.SpartanWeaponBuilder;

public class ScytheBuilder extends SpartanWeaponBuilder {

    public ScytheBuilder(ResourceLocation i) {
        super(i);
    }

    @Override
    public Item createObject() {
        var item = WeaponFactory.SCYTHE.create(material, new Item.Properties());

        setAttackDamage(item, attackDamage);

		setAttackSpeed(item, attackSpeed);

		setDuration(item, duration);

        return item;
    }
}
