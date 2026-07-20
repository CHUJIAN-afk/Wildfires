package first.wildfires.mixin.irons_spellbooks;

import com.mojang.logging.LogUtils;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.runtime.IIngredientManager;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * The Iron's Spellbooks JEI plugin eagerly builds every scroll, imbue, and upgrade
 * combination for the Arcane Anvil. The combinations are not useful in this pack's
 * JEI browser and can take tens of seconds to enumerate after joining a server.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.jei.ArcaneAnvilRecipeMaker", remap = false)
public class ArcaneAnvilRecipeMakerMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "getRecipes", at = @At("HEAD"), cancellable = true)
    private static void wildfires$skipArcaneAnvilJeiRecipes(
            IVanillaRecipeFactory vanillaRecipeFactory,
            IIngredientManager ingredientManager,
            CallbackInfoReturnable<List<?>> cir
    ) {
        LOGGER.info("[Wildfires JEI] Skipped Iron's Spellbooks Arcane Anvil recipe enumeration");
        cir.setReturnValue(List.of());
    }
}
