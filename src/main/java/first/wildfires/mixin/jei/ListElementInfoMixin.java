package first.wildfires.mixin.jei;

import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.ingredients.ListElementInfo;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ListElementInfo.class, remap = false)
public class ListElementInfoMixin {

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void wildfires$skipEmptyItemStacks(
        ITypedIngredient<?> typedIngredient,
        IIngredientManager ingredientManager,
        IModIdHelper modIdHelper,
        CallbackInfoReturnable<IListElementInfo<?>> cir
    ) {
        if (typedIngredient.getIngredient() instanceof ItemStack itemStack && itemStack.isEmpty()) {
            // JEI already skips null elements. Skip invalid empty stacks before it logs an exception.
            cir.setReturnValue(null);
        }
    }
}
