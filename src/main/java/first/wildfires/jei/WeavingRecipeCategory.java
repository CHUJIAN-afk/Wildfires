package first.wildfires.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import first.wildfires.Wildfires;
import first.wildfires.kinetic.loom.recipe.IngredientWithCount;
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
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
    public mezz.jei.api.recipe.@NotNull RecipeType<WeavingRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public @NotNull Component getTitle() {
        return Component.translatable("wildfires.jei.category.weaving");
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
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, WeavingRecipe recipe, @NotNull IFocusGroup focuses) {
        // 输入槽位 (左侧) - 使用带数量的成分
        int inputSlotX = 10;
        int inputSlotY = 45;
        List<IngredientWithCount> ingredientsWithCount = recipe.getIngredientsWithCount();

        if (!ingredientsWithCount.isEmpty()) {
            // 使用带数量的成分列表
            for (int i = 0; i < ingredientsWithCount.size() && i < 4; i++) {
                int row = i / 2;
                int col = i % 2;
                IngredientWithCount iwc = ingredientsWithCount.get(i);

                // 创建槽位并添加成分
                builder.addSlot(RecipeIngredientRole.INPUT, inputSlotX + col * 18, inputSlotY + row * 18)
                        .addIngredients(iwc.ingredient());

                // 如果数量大于1，需要显示数量提示
                // JEI 会自动从 Ingredient 中获取物品，但不会显示数量
                // 我们通过自定义渲染来显示数量
            }
        } else {
            // 兼容旧格式
            for (int i = 0; i < recipe.getIngredients().size() && i < 4; i++) {
                int row = i / 2;
                int col = i % 2;
                builder.addSlot(RecipeIngredientRole.INPUT, inputSlotX + col * 18, inputSlotY + row * 18)
                        .addIngredients(recipe.getIngredients().get(i));
            }
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
    public void draw(WeavingRecipe recipe, @NotNull IRecipeSlotsView recipeSlotsView, @NotNull GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 绘制背景
        background.draw(guiGraphics, 0, 0);
        IDrawable slot = asDrawable(AllGuiTextures.JEI_SLOT);
        IDrawable arrow = asDrawable(AllGuiTextures.JEI_DOWN_ARROW);
        IDrawable shadow = asDrawable(AllGuiTextures.JEI_SHADOW);

        // 绘制输入槽位背景框
        int inputSlotX = 10;
        int inputSlotY = 45;
        List<IngredientWithCount> ingredientsWithCount = recipe.getIngredientsWithCount();
        int ingredientCount = !ingredientsWithCount.isEmpty() ? ingredientsWithCount.size() : recipe.getIngredients().size();

        for (int i = 0; i < ingredientCount && i < 4; i++) {
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
        shadow.draw(guiGraphics, 45, 50);
        pose.popPose();

        // 渲染织布机动画
        AnimatedLoom.render(guiGraphics, recipe, 61, 10);

        // 绘制成分数量提示
        if (!ingredientsWithCount.isEmpty()) {
            pose.pushPose();
            pose.translate(0, 0, 200);
            drawIngredientCounts(guiGraphics, ingredientsWithCount, inputSlotX, inputSlotY);
            pose.popPose();
        }
    }

    /**
     * 绘制成分数量提示
     */
    private void drawIngredientCounts(GuiGraphics guiGraphics, List<IngredientWithCount> ingredientsWithCount, int baseX, int baseY) {
        for (int i = 0; i < ingredientsWithCount.size() && i < 4; i++) {
            IngredientWithCount iwc = ingredientsWithCount.get(i);
            int count = iwc.count();

            if (count > 1) {
                int row = i / 2;
                int col = i % 2;
                int x = baseX + col * 18 + 15;
                int y = baseY + row * 18 + 9;

                // 绘制数量文本（右下角小字）
                guiGraphics.drawString(
                        Minecraft.getInstance().font,
                        String.valueOf(count),
                        x - 5, y,
                        0xFFFFFF,
                        true
                );
            }
        }
    }

    private static IDrawable asDrawable(final AllGuiTextures texture) {
        return new IDrawable() {
            public int getWidth() {
                return texture.getWidth();
            }

            public int getHeight() {
                return texture.getHeight();
            }

            public void draw(@NotNull GuiGraphics graphics, int xOffset, int yOffset) {
                texture.render(graphics, xOffset, yOffset);
            }
        };
    }
}
