package first.wildfires.mixin.create;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import first.wildfires.utils.WildfiresUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(value = CrushingWheelControllerBlockEntity.class, remap = false)
public abstract class CrushingWheelControllerBlockEntityMixin {

    @ModifyExpressionValue(
            method = "applyRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/processing/recipe/ProcessingRecipe;rollResults()Ljava/util/List;"
            )
    )
    private List<ItemStack> wildfires$modifyRandomOutputChances(
            List<ItemStack> original,
            @Local(name = "recipe") Optional<ProcessingRecipe<RecipeWrapper>> recipe) {
        if (recipe.isEmpty()) {
            return original;
        }

        CrushingWheelControllerBlockEntity entity = (CrushingWheelControllerBlockEntity) (Object) this;
        ProcessingRecipe<RecipeWrapper> processingRecipe = recipe.get();
        List<ProcessingOutput> modifiedOutputs = WildfiresUtil.modifyProcessingOutputs(
                new ArrayList<>(processingRecipe.getRollableResults()), entity.getLevel(), entity.getBlockPos());
        return processingRecipe.rollResults(modifiedOutputs);
    }
}
