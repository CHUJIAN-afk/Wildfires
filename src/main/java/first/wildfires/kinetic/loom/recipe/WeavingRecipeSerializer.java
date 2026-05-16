package first.wildfires.kinetic.loom.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class WeavingRecipeSerializer implements RecipeSerializer<WeavingRecipe> {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wildfires/WeavingRecipe");

    public static final WeavingRecipeSerializer INSTANCE = new WeavingRecipeSerializer();

    @Override
    public WeavingRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
        LOGGER.info("Loading weaving recipe: {}", recipeId);
        NonNullList<Ingredient> ingredients = readIngredients(GsonHelper.getAsJsonArray(json, "ingredients"));
        NonNullList<ItemStack> outputs;
        try {
            outputs = readOutputs(GsonHelper.getAsJsonArray(json, "results"));
        } catch (CommandSyntaxException e) {
            throw new RuntimeException(e);
        }

        if (ingredients.isEmpty()) {
            throw new JsonParseException("No ingredients for weaving recipe");
        }
        if (outputs.isEmpty()) {
            throw new JsonParseException("No results for weaving recipe");
        }

        JsonObject colorObj = GsonHelper.getAsJsonObject(json, "color");
        int r = GsonHelper.getAsInt(colorObj, "r");
        int g = GsonHelper.getAsInt(colorObj, "g");
        int b = GsonHelper.getAsInt(colorObj, "b");
        int color = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);

        WeavingType weavingType = WeavingType.fromName(GsonHelper.getAsString(json, "weaving_type", "knitted_cloth"));

        return new WeavingRecipe(recipeId, ingredients, outputs, color, weavingType);
    }

    private NonNullList<Ingredient> readIngredients(JsonArray array) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (JsonElement element : array) {
            Ingredient ingredient = Ingredient.fromJson(element);
            if (!ingredient.isEmpty()) {
                ingredients.add(ingredient);
            }
        }
        return ingredients;
    }

    private NonNullList<ItemStack> readOutputs(JsonArray array) throws CommandSyntaxException {
        NonNullList<ItemStack> outputs = NonNullList.create();
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            ItemStack stack = CraftingHelper.getItemStack(obj, true, true);
            if (obj.has("count")) {
                stack.setCount(obj.get("count").getAsInt());
            }
            if (obj.has("nbt")) {
                stack.setTag(net.minecraft.nbt.TagParser.parseTag(obj.get("nbt").getAsString()));
            }
            outputs.add(stack);
        }
        return outputs;
    }

    @Nullable
    @Override
    public WeavingRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
        int ingredientCount = buffer.readVarInt();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(ingredientCount, Ingredient.EMPTY);
        for (int i = 0; i < ingredientCount; i++) {
            ingredients.set(i, Ingredient.fromNetwork(buffer));
        }

        int outputCount = buffer.readVarInt();
        NonNullList<ItemStack> outputs = NonNullList.withSize(outputCount, ItemStack.EMPTY);
        for (int i = 0; i < outputCount; i++) {
            outputs.set(i, buffer.readItem());
        }

        int color = buffer.readInt();
        WeavingType weavingType = WeavingType.values()[buffer.readVarInt()];

        return new WeavingRecipe(recipeId, ingredients, outputs, color, weavingType);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, WeavingRecipe recipe) {
        buffer.writeVarInt(recipe.getIngredients().size());
        for (Ingredient ingredient : recipe.getIngredients()) {
            ingredient.toNetwork(buffer);
        }

        buffer.writeVarInt(recipe.getOutputs().size());
        for (ItemStack output : recipe.getOutputs()) {
            buffer.writeItem(output);
        }

        buffer.writeInt(recipe.getColor());
        buffer.writeVarInt(recipe.getWeavingType().ordinal());
    }
}
