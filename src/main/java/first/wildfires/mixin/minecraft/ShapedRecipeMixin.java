package first.wildfires.mixin.minecraft;

import first.wildfires.mixin.tfc.AdvancedShapedRecipeAccessor;
import first.wildfires.register.ItemRegister;
import net.dries007.tfc.common.recipes.AdvancedShapedRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.stream.Stream;

@Mixin(ShapedRecipe.class)
public class ShapedRecipeMixin {
/*
    @ModifyVariable(
            method = "matches(Lnet/minecraft/world/inventory/CraftingContainer;IIZ)Z",
            at = @At("STORE"),
            name = "ingredient"
    )
    private Ingredient addMatches(Ingredient ingredient) {
        ShapedRecipe recipe = (ShapedRecipe) (Object) this;
        if (ingredient == Ingredient.EMPTY && recipe instanceof AdvancedShapedRecipe advancedShapedRecipe) {
            AdvancedShapedRecipeAccessor accessor = (AdvancedShapedRecipeAccessor) advancedShapedRecipe;
            if (accessor.getProviderResult().getEmptyStack().isDamageableItem()) {
                return new Ingredient(Stream.of(ItemRegister.CopperBoltItem.get()).map(ItemStack::new).map(Ingredient.ItemValue::new)) {

                    @Override
                    public boolean test(ItemStack stack) {
                        if (stack == null) return false;
                        return stack.isEmpty() || super.test(stack);
                    }

                };
            }
        }
        return ingredient;
    }
*/
}
