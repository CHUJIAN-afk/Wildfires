package first.wildfires.kinetic.loom.recipe;

import net.minecraft.world.item.crafting.RecipeType;

public class WeavingRecipeType implements RecipeType<WeavingRecipe> {

    public static final WeavingRecipeType INSTANCE = new WeavingRecipeType();

    private WeavingRecipeType() {
    }

    @Override
    public String toString() {
        return "wildfires:weaving";
    }
}
