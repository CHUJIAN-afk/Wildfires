package first.wildfires.mixin.tfc;

import first.wildfires.register.ItemRegister;
import net.dries007.tfc.common.recipes.AdvancedShapedRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AdvancedShapedRecipe.class, remap = false)
public class AdvancedShapedRecipeMixin {

    @Inject(
            method = "assemble(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN")
    )
    public void addDurability(CraftingContainer inventory, RegistryAccess registryAccess, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack tool = cir.getReturnValue();
        if (tool.isDamageableItem()) {
            int tempDuration = (int) (inventory.getItems().stream()
                    .filter(item -> item.is(ItemRegister.CopperBoltItem.get()))
                    .count() * 0.1f * tool.getMaxDamage());
            if (tempDuration > 0) {
                tool.getOrCreateTag().putInt("TempDuration", tempDuration);
            }
        }
    }

}
