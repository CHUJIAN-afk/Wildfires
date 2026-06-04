package first.wildfires.mixin.minecraft.attackspeed;

import net.minecraft.world.item.HoeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(HoeItem.class)
public class HoeAttackSpeedMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/DiggerItem;<init>(FFLnet/minecraft/world/item/Tier;Lnet/minecraft/tags/TagKey;Lnet/minecraft/world/item/Item$Properties;)V"),
            index = 1
    )
    private static float modifyHoeAttackSpeed(float attackSpeed) {
        return -3f;
    }
}
