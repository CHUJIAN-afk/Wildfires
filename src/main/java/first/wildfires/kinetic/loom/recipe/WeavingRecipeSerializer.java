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
import java.util.ArrayList;
import java.util.List;

public class WeavingRecipeSerializer implements RecipeSerializer<WeavingRecipe> {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wildfires/WeavingRecipe");

    public static final WeavingRecipeSerializer INSTANCE = new WeavingRecipeSerializer();

    @Override
    public WeavingRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
        LOGGER.info("Loading weaving recipe: {}", recipeId);

        // 解析成分（支持数量）
        List<IngredientWithCount> ingredientsWithCount = readIngredientsWithCount(GsonHelper.getAsJsonArray(json, "ingredients"));

        // 同时创建兼容的 NonNullList<Ingredient>
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (IngredientWithCount iwc : ingredientsWithCount) {
            ingredients.add(iwc.ingredient());
        }

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

        // 解析颜色 - 支持16位RGB格式 (#FFFFFF)
        int color;
        if (json.has("color")) {
            JsonElement colorElement = json.get("color");
            if (colorElement.isJsonObject()) {
                // 旧格式：{"r": 255, "g": 255, "b": 255}
                JsonObject colorObj = colorElement.getAsJsonObject();
                int r = GsonHelper.getAsInt(colorObj, "r");
                int g = GsonHelper.getAsInt(colorObj, "g");
                int b = GsonHelper.getAsInt(colorObj, "b");
                color = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
            } else {
                // 新格式：16位RGB字符串 "#FFFFFF"
                String colorStr = colorElement.getAsString();
                color = parseHexColor(colorStr);
            }
        } else {
            color = 0xFFFFFF; // 默认白色
        }

        WeavingType weavingType = WeavingType.fromName(GsonHelper.getAsString(json, "weaving_type", "knitted_cloth"));

        return new WeavingRecipe(recipeId, ingredients, ingredientsWithCount, outputs, color, weavingType);
    }

    /**
     * 解析16位RGB颜色字符串
     * 支持格式：#FFFFFF 或 FFFFFF
     */
    private int parseHexColor(String colorStr) {
        // 移除 # 前缀
        if (colorStr.startsWith("#")) {
            colorStr = colorStr.substring(1);
        }

        // 解析16进制颜色
        try {
            return Integer.parseInt(colorStr, 16);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid color format: {}, using default white", colorStr);
            return 0xFFFFFF;
        }
    }

    /**
     * 解析带数量的成分列表
     * 支持两种格式：
     * 1. 简单格式（无数量）：["minecraft:wool"]
     * 2. 对象格式（带数量）：[{"item": "minecraft:wool", "count": 4}]
     */
    private List<IngredientWithCount> readIngredientsWithCount(JsonArray array) {
        List<IngredientWithCount> ingredients = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                Ingredient ingredient = Ingredient.fromJson(obj);
                int count = GsonHelper.getAsInt(obj, "count", 1);
                if (!ingredient.isEmpty() && count > 0) {
                    ingredients.add(new IngredientWithCount(ingredient, count));
                }
            } else {
                // 简单格式，默认数量为1
                Ingredient ingredient = Ingredient.fromJson(element);
                if (!ingredient.isEmpty()) {
                    ingredients.add(new IngredientWithCount(ingredient, 1));
                }
            }
        }
        return ingredients;
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
        List<IngredientWithCount> ingredientsWithCount = new ArrayList<>();

        for (int i = 0; i < ingredientCount; i++) {
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            int count = buffer.readVarInt();
            ingredients.set(i, ingredient);
            ingredientsWithCount.add(new IngredientWithCount(ingredient, count));
        }

        int outputCount = buffer.readVarInt();
        NonNullList<ItemStack> outputs = NonNullList.withSize(outputCount, ItemStack.EMPTY);
        for (int i = 0; i < outputCount; i++) {
            outputs.set(i, buffer.readItem());
        }

        int color = buffer.readInt();
        WeavingType weavingType = WeavingType.values()[buffer.readVarInt()];

        return new WeavingRecipe(recipeId, ingredients, ingredientsWithCount, outputs, color, weavingType);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, WeavingRecipe recipe) {
        buffer.writeVarInt(recipe.getIngredients().size());

        List<IngredientWithCount> iwcList = recipe.getIngredientsWithCount();
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            recipe.getIngredients().get(i).toNetwork(buffer);
            // 写入数量（如果带数量列表存在且有对应元素）
            int count = (i < iwcList.size()) ? iwcList.get(i).count() : 1;
            buffer.writeVarInt(count);
        }

        buffer.writeVarInt(recipe.getOutputs().size());
        for (ItemStack output : recipe.getOutputs()) {
            buffer.writeItem(output);
        }

        buffer.writeInt(recipe.getColor());
        buffer.writeVarInt(recipe.getWeavingType().ordinal());
    }
}