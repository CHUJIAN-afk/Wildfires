package first.wildfires.mixin.tfc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.items.PanItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PanItem.class, remap = false)
public class PanItemMixin {

    @ModifyExpressionValue(
            method = "finishUsingItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/loot/LootParams$Builder;withParameter(Lnet/minecraft/world/level/storage/loot/parameters/LootContextParam;Ljava/lang/Object;)Lnet/minecraft/world/level/storage/loot/LootParams$Builder;",
                    ordinal = 0
            )
    )
    private LootParams.Builder withParameter(LootParams.Builder original, @Local(argsOnly = true) LivingEntity living) {
        return WildfiresUtil.modifyLootParams(original, living);
    }

}
