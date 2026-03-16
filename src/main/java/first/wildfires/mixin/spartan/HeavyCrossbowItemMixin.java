package first.wildfires.mixin.spartan;

import com.oblivioussp.spartanweaponry.api.WeaponMaterial;
import com.oblivioussp.spartanweaponry.item.HeavyCrossbowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = HeavyCrossbowItem.class,remap = false)
public abstract class HeavyCrossbowItemMixin extends CrossbowItem {

    @Shadow(remap = false)
    protected WeaponMaterial material;

    public HeavyCrossbowItemMixin(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return super.getMaxDamage(stack) == 0 ? (int) (this.material.getUses() * 1.5) : super.getMaxDamage(stack);
    }
}
