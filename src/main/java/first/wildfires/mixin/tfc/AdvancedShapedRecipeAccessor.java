package first.wildfires.mixin.tfc;

import net.dries007.tfc.common.recipes.AdvancedShapedRecipe;
import net.dries007.tfc.common.recipes.outputs.ItemStackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AdvancedShapedRecipe.class, remap = false)
public interface AdvancedShapedRecipeAccessor {
/*
    @Accessor
    ItemStackProvider getProviderResult();
*/
}
