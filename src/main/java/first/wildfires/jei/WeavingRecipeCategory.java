package first.wildfires.jei;

import first.wildfires.Wildfires;
import first.wildfires.kinetic.loom.recipe.WeavingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class WeavingRecipeCategory implements IRecipeCategory<WeavingRecipe> {

    public static final mezz.jei.api.recipe.RecipeType<WeavingRecipe> RECIPE_TYPE =
            mezz.jei.api.recipe.RecipeType.create(Wildfires.MODID, "weaving", WeavingRecipe.class);

    private final IDrawableStatic background;
    private final IDrawableStatic arrow;
    private final int width = 120;
    private final int height = 60;

    public WeavingRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(width, height);
        this.arrow = guiHelper.createDrawable(ResourceLocation.parse("minecraft:textures/gui/container/furnace.png"),
                79, 35, 24, 16);
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<WeavingRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("wildfires.jei.category.weaving");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return background;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WeavingRecipe recipe, IFocusGroup focuses) {
        // 输入槽位 (左侧)
        int inputSlotX = 5;
        int inputSlotY = 5;
        for (int i = 0; i < recipe.getIngredients().size() && i < 4; i++) {
            int row = i / 2;
            int col = i % 2;
            builder.addSlot(RecipeIngredientRole.INPUT, inputSlotX + col * 18, inputSlotY + row * 18)
                    .addIngredients(recipe.getIngredients().get(i));
        }

        // 输出槽位 (右侧)
        int outputSlotX = 85;
        int outputSlotY = 15;
        for (int i = 0; i < recipe.getOutputs().size() && i < 4; i++) {
            int row = i / 2;
            int col = i % 2;
            builder.addSlot(RecipeIngredientRole.OUTPUT, outputSlotX + col * 18, outputSlotY + row * 18)
                    .addItemStack(recipe.getOutputs().get(i));
        }
    }

    @Override
    public void draw(WeavingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        arrow.draw(guiGraphics, 45, 22);
    }
}