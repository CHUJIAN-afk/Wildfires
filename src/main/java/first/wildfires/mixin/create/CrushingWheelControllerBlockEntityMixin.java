package first.wildfires.mixin.create;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.foundation.item.ItemHelper;
import first.wildfires.utils.WildfiresUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(CrushingWheelControllerBlockEntity.class)
public class CrushingWheelControllerBlockEntityMixin {

    @ModifyExpressionValue(
            method = "applyRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/processing/recipe/ProcessingRecipe;rollResults()Ljava/util/List;")
    )
    private List<ItemStack> applyRecipe(List<ItemStack> original, @Local(name = "recipe") Optional<ProcessingRecipe<RecipeWrapper>> recipe) {
        if (recipe.isPresent()) {
            ProcessingRecipe<RecipeWrapper> processingRecipe = recipe.get();
            List<ProcessingOutput> outputList = new ArrayList<>();
            for (ProcessingOutput result : processingRecipe.getRollableResults()) {
                float chance = result.getChance();
                CrushingWheelControllerBlockEntity entity = (CrushingWheelControllerBlockEntity) (Object) this;
                Level level = entity.getLevel();
                if (level != null) {
                    chance *= (1 + WildfiresUtil.getLuck(level, Player.class, new AABB(entity.getBlockPos()).inflate(32)) * 0.1f);
                }
                outputList.add(new ProcessingOutput(result.getStack(), chance));
            }
            return processingRecipe.rollResults(outputList);
        }
        return original;
    }

}
