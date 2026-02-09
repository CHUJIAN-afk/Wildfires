package first.wildfires.mixin.minecraft;

import first.wildfires.utils.WildfiresUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Random;

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
            if (tag.getBoolean("Broken") && Math.random() < 0.25) {
                amount++;
            }
            int polish = tag.getInt("Polish");
            if (polish > 0) {
                tag.putInt("Polish", polish - amount);
            }
            if (polish <= 0) {
                tag.remove("Polish");
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
