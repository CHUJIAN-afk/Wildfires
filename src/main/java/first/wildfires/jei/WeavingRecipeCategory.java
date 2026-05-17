package first.wildfires.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import first.wildfires.Wildfires;
import first.wildfires.kinetic.loom.recipe.WeavingRecipe;
import first.wildfires.register.BlockRegister;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class WeavingRecipeCategory implements IRecipeCategory<WeavingRecipe> {

    public static final mezz.jei.api.recipe.RecipeType<WeavingRecipe> RECIPE_TYPE = mezz.jei.api.recipe.RecipeType.create(Wildfires.MODID, "weaving", WeavingRecipe.class);

    private final IGuiHelper helper;
    private final IDrawableStatic background;
    private final int width = 200;
    private final int height = 100;

    public WeavingRecipeCategory(IGuiHelper guiHelper) {
        this.helper = guiHelper;
        this.background = helper.createBlankDrawable(width, height);
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
        return helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BlockRegister.LoomControlBlock.asItem()));
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
        int inputSlotX = 10;
        int inputSlotY = 45;
        for (int i = 0; i < recipe.getIngredients().size() && i < 4; i++) {
            int row = i / 2;
            int col = i % 2;
            builder.addSlot(RecipeIngredientRole.INPUT, inputSlotX + col * 18, inputSlotY + row * 18)
                    .addIngredients(recipe.getIngredients().get(i));
        }

        // 输出槽位 (右侧)
        int outputSlotX = 165;
        int outputSlotY = 62;
        for (int i = 0; i < recipe.getOutputs().size() && i < 4; i++) {
            int row = i / 2;
            int col = i % 2;
            builder.addSlot(RecipeIngredientRole.OUTPUT, outputSlotX + col * 18, outputSlotY + row * 18)
                    .addItemStack(recipe.getOutputs().get(i));
        }
    }

    @Override
    public void draw(WeavingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 绘制背景
        background.draw(guiGraphics, 0, 0);
        IDrawable slot = asDrawable(AllGuiTextures.JEI_SLOT);
        IDrawable arrow = asDrawable(AllGuiTextures.JEI_DOWN_ARROW);
        IDrawable shadow = asDrawable(AllGuiTextures.JEI_SHADOW);

        // 绘制输入槽位背景框
        int inputSlotX = 10;
        int inputSlotY = 45;
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            int row = i / 2;
            int col = i % 2;
            slot.draw(guiGraphics, inputSlotX + col * 18 - 1, inputSlotY + row * 18 - 1);
        }

        // 绘制输出槽位背景框
        int outputSlotX = 165;
        int outputSlotY = 62;
        for (int i = 0; i < recipe.getOutputs().size(); i++) {
            int row = i / 2;
            int col = i % 2;
            slot.draw(guiGraphics, outputSlotX + col * 18 - 1, outputSlotY + row * 18 - 1);
        }

        // 绘制箭头
        arrow.draw(guiGraphics, 160, 45);

        // 绘制织布机下方的阴影
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.scale(1.5f, 1.5f, 1.5f);
        shadow.draw(guiGraphics, 45, 55);
        pose.popPose();

        // 渲染织布机动画
        AnimatedLoom.render(guiGraphics, recipe, 45, 20);
    }

    private static IDrawable asDrawable(final AllGuiTextures texture) {
        return new IDrawable() {
            public int getWidth() {
                return texture.getWidth();
            }

            public int getHeight() {
                return texture.getHeight();
            }

            public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
                texture.render(graphics, xOffset, yOffset);
            }
        };
    }
}
