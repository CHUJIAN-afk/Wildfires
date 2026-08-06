package first.wildfires.mixin.create;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import first.wildfires.utils.WildfiresUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(value = ProcessingRecipe.class, remap = false)
public abstract class ProcessingRecipeMixin {

    @ModifyVariable(
            method = "rollResults(Ljava/util/List;)Ljava/util/List;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private List<ProcessingOutput> wildfires$modifyRandomOutputChances(List<ProcessingOutput> outputs) {
        return WildfiresUtil.modifyProcessingOutputs(outputs, WildfiresUtil.getKineticTickContext());
    }
}
