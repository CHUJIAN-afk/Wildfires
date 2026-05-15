package first.wildfires.register;

import first.wildfires.Wildfires;
import first.wildfires.kinetic.loom.recipe.WeavingRecipe;
import first.wildfires.kinetic.loom.recipe.WeavingRecipeSerializer;
import first.wildfires.kinetic.loom.recipe.WeavingRecipeType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WeavingRecipeRegister {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Wildfires.MODID);

    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, Wildfires.MODID);

    public static final RegistryObject<RecipeSerializer<WeavingRecipe>> WEAVING_SERIALIZER = SERIALIZERS.register("weaving", () -> WeavingRecipeSerializer.INSTANCE);

    public static final RegistryObject<RecipeType<WeavingRecipe>> WEAVING_TYPE = TYPES.register("weaving", () -> WeavingRecipeType.INSTANCE);

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
        TYPES.register(eventBus);
    }
}
