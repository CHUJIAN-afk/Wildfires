package first.wildfires.compats.kubejs.spartan;

import com.oblivioussp.spartanweaponry.api.WeaponMaterial;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import first.wildfires.mixin.spartan.SwordBaseItemAccessor;
import first.wildfires.mixin.spartan.ThrowingWeaponItemAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public abstract class SpartanWeaponBuilder extends ItemBuilder {

    public float attackDamage = 0;

    public double attackSpeed = 0;

    public int duration = 0;

    public WeaponMaterial material;

    public SpartanWeaponBuilder(ResourceLocation i) {
        super(i);
    }

    public SpartanWeaponBuilder material(WeaponMaterial material) {
        this.material = material;

        return this;
    }

    protected static void setAttackDamage(Item item, float damage) {
        if (damage <= 0)
            return;

        if (item instanceof SwordBaseItemAccessor swordBaseItem) {
            swordBaseItem.setAttackDamage(damage);
        } else if (item instanceof ThrowingWeaponItemAccessor throwingWeaponItem) {
            throwingWeaponItem.setAttackDamage(damage);
        }

    }

    protected static void setAttackSpeed(Item item, double attackSpeed) {
        if (attackSpeed <= 0)
            return;

        if (item instanceof SwordBaseItemAccessor swordBaseItem) {
            swordBaseItem.setAttackSpeed(attackSpeed);
        }
        else if (item instanceof ThrowingWeaponItemAccessor throwingWeaponItem) {
            throwingWeaponItem.setAttackSpeed(attackSpeed);
        }
    }

    protected static void setDuration(Item item, int duration) {
        if (duration <= 0)
            return;

        //item.maxDamage = duration;
    }

    public SpartanWeaponBuilder setAttackDamage(float attackDamage) {
        this.attackDamage =  attackDamage;

        return this;
    }

    public SpartanWeaponBuilder setAttackSpeed(double attackSpeed) {
        this.attackSpeed =  attackSpeed;

        return this;
    }

    public SpartanWeaponBuilder setDuration(int duration) {
        this.duration = duration;

        return this;
    }
}
