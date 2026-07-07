package first.wildfires.mixin.tfc;

import first.wildfires.utils.ItemDamageContext;
import net.dries007.tfc.common.items.ToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ToolItem.class, remap = false)
public class ToolItemMixin {

    @Inject(method = "mineBlock", at = @At("HEAD"), remap = true)
    private void wildfires$markInstantBlockBreak(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        CompoundTag tag = stack.getTag();
        ItemDamageContext.setInstantBlockBreak(!level.isClientSide() && tag != null && tag.getInt("Polish") > 0 && state.getDestroySpeed(level, pos) == 0.0F);
    }

    @Inject(method = "mineBlock", at = @At("RETURN"), remap = true)
    private void wildfires$clearInstantBlockBreak(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        ItemDamageContext.clearInstantBlockBreak();
    }
}
