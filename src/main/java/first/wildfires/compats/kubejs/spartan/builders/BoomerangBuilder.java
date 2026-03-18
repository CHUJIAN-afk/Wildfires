package first.wildfires.compats.kubejs.spartan.builders;

import com.oblivioussp.spartanweaponry.util.WeaponFactory;
import first.wildfires.compats.kubejs.spartan.SpartanWeaponBuilder;
import first.wildfires.mixin.spartan.ThrowingWeaponItemAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class BoomerangBuilder extends SpartanWeaponBuilder {

    public int maxAmmo = 1;

    public BoomerangBuilder(ResourceLocation i) {
        super(i);
    }

    @Override
    public Item createObject() {
        var item = WeaponFactory.BOOMERANG.create(material, new Item.Properties());

        if (item instanceof ThrowingWeaponItemAccessor throwingWeaponItem)
            throwingWeaponItem.setMaxAmmo(maxAmmo);

        setAttackDamage(item, attackDamage);

		setAttackSpeed(item, attackSpeed);

        return item;
    }

    public BoomerangBuilder setAmmo(int number) {
        maxAmmo = number;

        return this;
    }
}
