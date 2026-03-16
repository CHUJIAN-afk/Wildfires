package first.wildfires.compats.kubejs.spartan.builders;

import com.oblivioussp.spartanweaponry.util.WeaponFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import first.wildfires.compats.kubejs.spartan.SpartanWeaponBuilder;
import first.wildfires.mixin.spartan.ThrowingWeaponItemAccessor;

public class TomahawkBuilder extends SpartanWeaponBuilder {

    public int maxAmmo = 8;

    public TomahawkBuilder(ResourceLocation i) {
        super(i);
    }

    @Override
    public Item createObject() {
        var item = WeaponFactory.TOMAHAWK.create(material, new Item.Properties());

        if (item instanceof ThrowingWeaponItemAccessor throwingWeaponItem)
            throwingWeaponItem.setMaxAmmo(maxAmmo);

        setAttackDamage(item, attackDamage);

		setAttackSpeed(item, attackSpeed);

        return item;
    }

    public TomahawkBuilder setAmmo(int number) {
        maxAmmo = number;

        return this;
    }
}
