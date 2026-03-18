package first.wildfires.mixin.spartan;

import com.oblivioussp.spartanweaponry.api.WeaponMaterial;
import com.oblivioussp.spartanweaponry.item.SwordBaseItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = SwordBaseItem.class,remap = false)
public abstract class SwordBaseItemMixin extends SwordItem {

    @Shadow(remap = false)
    protected WeaponMaterial material;

    public SwordBaseItemMixin(Tier pTier, int pAttackDamageModifier, float pAttackSpeedModifier, Properties pProperties) {
        super(pTier, pAttackDamageModifier, pAttackSpeedModifier, pProperties);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return super.getMaxDamage(stack) == 0 ? this.material.getUses() : super.getMaxDamage(stack);
    }
}
