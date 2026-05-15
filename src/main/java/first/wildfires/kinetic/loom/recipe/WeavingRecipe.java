package first.wildfires.kinetic.loom.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public class WeavingRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final NonNullList<Ingredient> ingredients;
    private final NonNullList<ItemStack> outputs;
    private final int color;
    private final WeavingType weavingType;

    public WeavingRecipe(ResourceLocation id, NonNullList<Ingredient> ingredients,
                         NonNullList<ItemStack> outputs, int color, WeavingType weavingType) {
        this.id = id;
        this.ingredients = ingredients;
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
