package first.wildfires.mixin.minecraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import first.wildfires.utils.ItemDamageContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @ModifyVariable(
            method = "hurt",
            ordinal = 0,
            at = @At("HEAD"),
            argsOnly = true
    )
    private int modifyPAmount(int amount) {
        ItemStack itemStack = (ItemStack) (Object) this;
        int remove = 0;
        CompoundTag tag = itemStack.getTag();
        if (itemStack.isDamageableItem() && tag != null) {
            if (tag.getBoolean("Broken")) {
                amount = amount / 2 + 1;
            }
            int quenching = tag.getInt("Quenching");
            if (quenching > 0 && Math.random() < (1 - 1.0 / (1 + quenching / 900.0))) {
                amount = 0;
            }
            int polish = tag.getInt("Polish");
            if (polish > 0 && !ItemDamageContext.isInstantBlockBreak()) {
                tag.putInt("Polish", polish - amount);
            }
            if (polish <= 0) {
                tag.remove("Polish");
            }
            int reinforcement = tag.getInt("Reinforcement");
            if (amount > 0 && tag.contains("Reinforcement")) {
                double reinforcementChance = (reinforcement * 5 - 20) / 100.0;
                if (reinforcementChance < 0) {
                    if (Math.random() < -reinforcementChance) {
                        amount++;
                    }
                } else if (Math.random() < reinforcementChance) {
                    amount = 0;
                }
            }
            int tempDuration = tag.getInt("TempDuration");
            if (tempDuration > 0) {
                if (tempDuration > amount) {
                    remove = amount;
                    tag.putInt("TempDuration", tempDuration - amount);
                } else {
                    remove = tempDuration;
                    tag.remove("TempDuration");
                }
            }
        }
        return amount - remove;
    }

}
