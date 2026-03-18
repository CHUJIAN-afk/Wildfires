package first.wildfires.compats.kubejs.spartan.builders;

import com.oblivioussp.spartanweaponry.item.SwordBaseItem;
import com.oblivioussp.spartanweaponry.util.WeaponArchetype;
import first.wildfires.compats.kubejs.spartan.SpartanWeaponBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class ClubBuilder extends SpartanWeaponBuilder {

    public ClubBuilder(ResourceLocation i) {
        super(i);
    }

    @Override
    public Item createObject() {
        var item = new SwordBaseItem(new Item.Properties(), material, WeaponArchetype.CLUB, 4.0F, 1.0F, 1.3D);

        setAttackDamage(item, attackDamage);

        setAttackSpeed(item, attackSpeed);

        setDuration(item, duration);

        return item;
    }
}
