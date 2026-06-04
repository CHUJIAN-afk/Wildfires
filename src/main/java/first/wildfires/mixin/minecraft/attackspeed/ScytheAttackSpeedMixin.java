package first.wildfires.mixin.minecraft.attackspeed;

import net.dries007.tfc.common.items.ScytheItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ScytheItem.class)
public class ScytheAttackSpeedMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/common/items/ToolItem;<init>(Lnet/minecraft/world/item/Tier;FFLnet/minecraft/tags/TagKey;Lnet/minecraft/world/item/Item$Properties;)V"),
            index = 2
    )
    private static float modifyScytheAttackSpeed(float attackSpeed) {
        return -2.5f;
    }
}
