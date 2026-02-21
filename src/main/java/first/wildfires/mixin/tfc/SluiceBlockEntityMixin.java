package first.wildfires.mixin.tfc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.blockentities.SluiceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SluiceBlockEntity.class)
public class SluiceBlockEntityMixin {

    @ModifyExpressionValue(
            method = "serverTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/loot/LootParams$Builder;withOptionalParameter(Lnet/minecraft/world/level/storage/loot/parameters/LootContextParam;Ljava/lang/Object;)Lnet/minecraft/world/level/storage/loot/LootParams$Builder;"
            )
    )
    private static LootParams.Builder LootParams$Builder(LootParams.Builder original, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return WildfiresUtil.modifyLootParams(original, level, Player.class, new AABB(pos).inflate(32));
    }

}
