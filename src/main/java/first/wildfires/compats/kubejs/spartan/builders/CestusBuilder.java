package first.wildfires.compats.kubejs.spartan.builders;

import com.oblivioussp.spartanweaponry.item.SwordBaseItem;
import com.oblivioussp.spartanweaponry.util.WeaponArchetype;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import first.wildfires.compats.kubejs.spartan.SpartanWeaponBuilder;

public class CestusBuilder extends SpartanWeaponBuilder {

    public CestusBuilder(ResourceLocation i) {
        super(i);
    }

    @Override
    public Item createObject() {
        var item = new SwordBaseItem(new Item.Properties(), material, WeaponArchetype.CESTUS, 2.0F, 0.5F, 3.5D);

        setAttackDamage(item, attackDamage);

        setAttackSpeed(item, attackSpeed);

        setDuration(item, duration);

        return item;
    }
}
