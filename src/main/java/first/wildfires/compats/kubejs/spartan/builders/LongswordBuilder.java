package first.wildfires.compats.kubejs.spartan.builders;

import com.oblivioussp.spartanweaponry.util.WeaponFactory;
import first.wildfires.compats.kubejs.spartan.SpartanWeaponBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class LongswordBuilder extends SpartanWeaponBuilder {

    public LongswordBuilder(ResourceLocation i) {
        super(i);
    }

    @Override
    public Item createObject() {
        var item = WeaponFactory.LONGSWORD.create(material, new Item.Properties());

        setAttackDamage(item, attackDamage);

		setAttackSpeed(item, attackSpeed);

		setAttackSpeed(item, attackSpeed);

        return item;
    }
}
