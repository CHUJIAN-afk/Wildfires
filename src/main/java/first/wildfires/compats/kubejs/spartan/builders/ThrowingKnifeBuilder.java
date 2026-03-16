package first.wildfires.compats.kubejs.spartan.builders;

import com.oblivioussp.spartanweaponry.util.WeaponFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import first.wildfires.compats.kubejs.spartan.SpartanWeaponBuilder;
import first.wildfires.mixin.spartan.ThrowingWeaponItemAccessor;

public class ThrowingKnifeBuilder extends SpartanWeaponBuilder {

    public int maxAmmo = 16;

    public ThrowingKnifeBuilder(ResourceLocation i) {
        super(i);
    }

    @Override
    public Item createObject() {
        var item = WeaponFactory.THROWING_KNIFE.create(material, new Item.Properties());

        if (item instanceof ThrowingWeaponItemAccessor throwingWeaponItem)
            throwingWeaponItem.setMaxAmmo(maxAmmo);

        setAttackDamage(item, attackDamage);

        setAttackSpeed(item, attackSpeed);

        return item;
    }

    public ThrowingKnifeBuilder setAmmo(int number) {
        maxAmmo = number;

        return this;
    }
}
