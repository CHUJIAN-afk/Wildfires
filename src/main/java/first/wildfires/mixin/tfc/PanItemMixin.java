package first.wildfires.mixin.tfc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.items.PanItem;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Pannable;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

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
