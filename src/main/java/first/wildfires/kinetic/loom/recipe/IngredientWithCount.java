package first.wildfires.kinetic.loom.recipe;

import net.minecraft.world.item.crafting.Ingredient;

/**
 * 带数量的成分
 */
public record IngredientWithCount(Ingredient ingredient, int count) {

    public boolean isEmpty() {
        return ingredient.isEmpty() || count <= 0;
    }
}
