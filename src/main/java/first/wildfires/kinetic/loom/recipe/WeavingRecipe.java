package first.wildfires.kinetic.loom.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class WeavingRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final NonNullList<Ingredient> ingredients;
    private final List<IngredientWithCount> ingredientsWithCount;
    private final NonNullList<ItemStack> outputs;
    private final int color;
    private final WeavingType weavingType;

    public WeavingRecipe(ResourceLocation id, NonNullList<Ingredient> ingredients,
                         List<IngredientWithCount> ingredientsWithCount,
                         NonNullList<ItemStack> outputs, int color, WeavingType weavingType) {
        this.id = id;
        this.ingredients = ingredients;
        this.ingredientsWithCount = ingredientsWithCount;
        this.outputs = outputs;
        this.color = color;
        this.weavingType = weavingType;
    }

    // 兼容旧构造函数
    public WeavingRecipe(ResourceLocation id, NonNullList<Ingredient> ingredients,
                         NonNullList<ItemStack> outputs, int color, WeavingType weavingType) {
        this.id = id;
        this.ingredients = ingredients;
        this.ingredientsWithCount = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            this.ingredientsWithCount.add(new IngredientWithCount(ingredient, 1));
        }
        this.outputs = outputs;
        this.color = color;
        this.weavingType = weavingType;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return WeavingRecipeSerializer.INSTANCE;
    }

    @Override
    public RecipeType<?> getType() {
        return WeavingRecipeType.INSTANCE;
    }

    @Override
    public boolean matches(Container container, Level level) {
        if (level.isClientSide()) return false;

        // 使用带数量的匹配逻辑
        if (!ingredientsWithCount.isEmpty()) {
            // 创建容器物品的副本用于匹配
            List<ItemStack> availableItems = new ArrayList<>();
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i).copy();
                if (!stack.isEmpty()) {
                    availableItems.add(stack);
                }
            }

            // 检查每个成分需求
            for (IngredientWithCount iwc : ingredientsWithCount) {
                int needed = iwc.count();
                int found = 0;

                for (int i = 0; i < availableItems.size() && found < needed; i++) {
                    ItemStack available = availableItems.get(i);
                    if (!available.isEmpty() && iwc.ingredient().test(available)) {
                        int canTake = Math.min(available.getCount(), needed - found);
                        available.shrink(canTake);
                        found += canTake;
                    }
                }

                if (found < needed) {
                    return false;
                }
            }
            return true;
        }

        // 兼容旧逻辑
        int matchedIngredients = 0;
        boolean[] used = new boolean[container.getContainerSize()];

        for (Ingredient ingredient : ingredients) {
            boolean found = false;
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (!used[i]) {
                    ItemStack stack = container.getItem(i);
                    if (ingredient.test(stack)) {
                        used[i] = true;
                        found = true;
                        matchedIngredients++;
                        break;
                    }
                }
            }
            if (!found) return false;
        }

        return matchedIngredients == ingredients.size();
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }

    /**
     * 获取带数量的成分列表
     */
    public List<IngredientWithCount> getIngredientsWithCount() {
        return ingredientsWithCount;
    }

    public NonNullList<ItemStack> getOutputs() {
        return outputs;
    }

    public int getColor() {
        return color;
    }

    public int getRed() {
        return (color >> 16) & 0xFF;
    }

    public int getGreen() {
        return (color >> 8) & 0xFF;
    }

    public int getBlue() {
        return color & 0xFF;
    }

    public WeavingType getWeavingType() {
        return weavingType;
    }
}
