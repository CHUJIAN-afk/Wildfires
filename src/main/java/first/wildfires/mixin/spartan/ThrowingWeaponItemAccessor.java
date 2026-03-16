package first.wildfires.mixin.spartan;

import com.oblivioussp.spartanweaponry.item.ThrowingWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ThrowingWeaponItem.class,remap = false)
public interface ThrowingWeaponItemAccessor {

    @Accessor("attackDamage")
    void setAttackDamage(float damage);

    @Accessor("attackSpeed")
    void setAttackSpeed(double speed);

    @Accessor("maxAmmo")
    void setMaxAmmo(int maxAmmo);
}
