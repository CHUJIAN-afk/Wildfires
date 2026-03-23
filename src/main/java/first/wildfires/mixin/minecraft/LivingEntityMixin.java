package first.wildfires.mixin.minecraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import first.wildfires.register.AttributeRegister;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @ModifyExpressionValue(
            method = "getDamageAfterArmorAbsorb",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(FFF)F"
            )
    )
    
    private float getDamageAfterArmorAbsorb(float original, @Local(argsOnly = true) DamageSource damageSource, @Local(argsOnly = true) float damageAmount) {
        LivingEntity target = (LivingEntity) (Object) this;
        int armorValue = target.getArmorValue();
        if (damageSource.getEntity() instanceof LivingEntity attacker) {
            int armorPenetration = (int) (attacker.getAttribute(AttributeRegister.ArmorPenetration.get()) != null ? attacker.getAttributeValue(AttributeRegister.ArmorPenetration.get()) : 0);
            int armorToughness = (int) (target.getAttribute(Attributes.ARMOR_TOUGHNESS) != null ? target.getAttributeValue(Attributes.ARMOR_TOUGHNESS) : 0);
            armorPenetration -= armorToughness;
            armorPenetration = Math.max(armorPenetration, 0);
            armorValue -= armorPenetration;
            armorValue = Math.max(armorValue, 0);
        }
        float removed = armorValue > 0 ? Math.min( 0.95f*damageAmount, damageAmount * armorValue * 0.05f) : 0;
        if (removed > 0) {
            EquipmentSlot[] equipmentSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (EquipmentSlot equipmentSlot : equipmentSlots) {
                ItemStack itemStack = target.getItemBySlot(equipmentSlot);
                int sum = itemStack.getAttributeModifiers(equipmentSlot).get(Attributes.ARMOR).stream()
                        .filter(modifier -> modifier.getOperation() == AttributeModifier.Operation.ADDITION)
                        .map(AttributeModifier::getAmount)
                        .mapToInt(Double::intValue)
                        .sum();
                itemStack.hurtAndBreak((int) removed * (sum / armorValue), target, living -> {
                });
            }
        }
        return damageAmount - removed;
    }

}
