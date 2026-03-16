package first.wildfires.mixin.spartan;

import com.oblivioussp.spartanweaponry.item.SwordBaseItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SwordBaseItem.class, remap = false)
public interface SwordBaseItemAccessor {

    @Accessor("attackDamage")
    void setAttackDamage(float damage);

    @Accessor("attackSpeed")
    void setAttackSpeed(double speed);

}
